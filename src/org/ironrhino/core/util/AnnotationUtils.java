package org.ironrhino.core.util;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.ironrhino.core.util.AppInfo.Stage;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;

import lombok.Value;
import lombok.experimental.UtilityClass;

@SuppressWarnings("unchecked")
@UtilityClass
public class AnnotationUtils {

	private static Map<Key, Set<Method>> annotatedMethodsCache = new ConcurrentHashMap<>(64);
	private static Map<Key, Set<String>> annotatedPropertyNamesCache = new ConcurrentHashMap<>(64);
	private static Map<Key, Map<String, ? extends Annotation>> annotatedPropertyNameAndAnnotationsCache = new ConcurrentHashMap<>(
			64);

	private static ValueThenKeyComparator<Method, Integer> comparator = new ValueThenKeyComparator<Method, Integer>() {
		@Override
		protected int compareKey(Method a, Method b) {
			return a.getName().compareTo(b.getName());
		}
	};

	public static Method getAnnotatedMethod(Class<?> clazz, Class<? extends Annotation> annotationClass) {
		clazz = ReflectionUtils.getActualClass(clazz);
		Iterator<Method> it = getAnnotatedMethods(clazz, annotationClass).iterator();
		if (it.hasNext())
			return it.next();
		return null;
	}

	public static Set<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotationClass) {
		clazz = ReflectionUtils.getActualClass(clazz);
		Key key = new Key(clazz, annotationClass);
		Set<Method> methods = annotatedMethodsCache.get(key);
		if (methods == null || AppInfo.getStage() == Stage.DEVELOPMENT) {
			final Map<Method, Integer> map = new HashMap<Method, Integer>();
			try {
				for (Method m : clazz.getMethods()) {
					// public methods include default methods on interface or
					// super class
					if (m.getAnnotation(annotationClass) != null) {
						int mod = m.getModifiers();
						if (Modifier.isStatic(mod) || Modifier.isAbstract(mod))
							continue;
						Order o = m.getAnnotation(Order.class);
						map.put(m, o != null ? o.value() : 0);
					}
				}
				for (Class<?> c = clazz; c != Object.class; c = c.getSuperclass()) {
					for (Method m : c.getDeclaredMethods()) {
						// protected and private methods on super class
						if (m.getAnnotation(annotationClass) != null) {
							int mod = m.getModifiers();
							if (Modifier.isStatic(mod) || Modifier.isAbstract(mod) || Modifier.isPublic(mod))
								continue;
							m.setAccessible(true);
							Order o = m.getAnnotation(Order.class);
							map.put(m, o != null ? o.value() : 0);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			List<Map.Entry<Method, Integer>> list = new ArrayList<Map.Entry<Method, Integer>>(map.entrySet());
			list.sort(comparator);
			methods = new LinkedHashSet<>();
			for (Map.Entry<Method, Integer> entry : list)
				methods.add(entry.getKey());
			methods = Collections.unmodifiableSet(methods);
			annotatedMethodsCache.put(key, methods);
		}
		return methods;
	}

	public static Set<String> getAnnotatedPropertyNames(Class<?> clazz, Class<? extends Annotation> annotationClass) {
		clazz = ReflectionUtils.getActualClass(clazz);
		Key key = new Key(clazz, annotationClass);
		Set<String> set = annotatedPropertyNamesCache.get(key);
		if (set == null || AppInfo.getStage() == Stage.DEVELOPMENT) {
			set = new HashSet<>();
			try {
				for (Class<?> cls = clazz; cls != Object.class; cls = cls.getSuperclass()) {
					Field[] fs = cls.getDeclaredFields();
					for (Field f : fs)
						if (f.getAnnotation(annotationClass) != null)
							set.add(f.getName());
				}
				PropertyDescriptor[] pds = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
				for (PropertyDescriptor pd : pds)
					if (pd.getReadMethod() != null && pd.getReadMethod().getAnnotation(annotationClass) != null)
						set.add(pd.getName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			set = Collections.unmodifiableSet(set);
			annotatedPropertyNamesCache.put(key, set);
		}
		return set;
	}

	@SafeVarargs
	public static Map<String, Object> getAnnotatedPropertyNameAndValues(Object object,
			Class<? extends Annotation>... annotationClass) {
		if (annotationClass.length == 0)
			return Collections.emptyMap();
		Map<String, Object> map = new HashMap<String, Object>();
		Set<String> propertyNames = new HashSet<>();
		for (Class<? extends Annotation> clz : annotationClass)
			propertyNames.addAll(getAnnotatedPropertyNames(object.getClass(), clz));
		BeanWrapperImpl bw = new BeanWrapperImpl(object);
		try {
			for (String key : propertyNames) {
				map.put(key, bw.getPropertyValue(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}

	public static <T extends Annotation> Map<String, T> getAnnotatedPropertyNameAndAnnotations(Class<?> clazz,
			Class<T> annotationClass) {
		clazz = ReflectionUtils.getActualClass(clazz);
		Key key = new Key(clazz, annotationClass);
		Map<String, T> map = (Map<String, T>) annotatedPropertyNameAndAnnotationsCache.get(key);
		if (map == null || AppInfo.getStage() == Stage.DEVELOPMENT) {
			map = new HashMap<String, T>();
			try {
				for (Class<?> cls = clazz; cls != Object.class; cls = cls.getSuperclass()) {
					Field[] fs = cls.getDeclaredFields();
					for (Field f : fs)
						if (f.getAnnotation(annotationClass) != null)
							map.put(f.getName(), f.getAnnotation(annotationClass));
				}
				PropertyDescriptor[] pds = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
				for (PropertyDescriptor pd : pds)
					if (pd.getReadMethod() != null && pd.getReadMethod().getAnnotation(annotationClass) != null)
						map.put(pd.getName(), pd.getReadMethod().getAnnotation(annotationClass));
			} catch (Exception e) {
				e.printStackTrace();
			}
			map = Collections.unmodifiableMap(map);
			annotatedPropertyNameAndAnnotationsCache.put(key, map);
		}
		return map;
	}

	public static <T extends Annotation> T getAnnotation(AnnotatedTypeMetadata metadata, Class<T> annotationClass) {
		Map<String, Object> attributes = metadata.getAnnotationAttributes(annotationClass.getName());
		if (attributes == null)
			return null;
		return org.springframework.core.annotation.AnnotationUtils.synthesizeAnnotation(attributes, annotationClass,
				null);
	}

	public static <T extends Annotation> T[] getAnnotationsByType(AnnotatedTypeMetadata metadata,
			Class<T> annotationClass) {
		if (metadata.isAnnotated(annotationClass.getName())) {
			T[] array = (T[]) Array.newInstance(annotationClass, 1);
			array[0] = getAnnotation(metadata, annotationClass);
			return array;
		} else {
			Class<? extends Annotation> annotationContainer = getAnnotationContainer(annotationClass);
			if (annotationContainer != null) {
				Annotation anno = getAnnotation(metadata, annotationContainer);
				if (anno != null)
					try {
						return (T[]) annotationContainer.getMethod("value").invoke(anno);
					} catch (Exception e) {
						e.printStackTrace();
					}
			}
		}
		return (T[]) Array.newInstance(annotationClass, 0);
	}

	public static Class<? extends Annotation> getAnnotationContainer(Class<?> annotationClass) {
		Repeatable r = annotationClass.getAnnotation(Repeatable.class);
		if (r == null)
			return null;
		return r.value();
	}

	@Value
	private static final class Key {
		Class<?> clazz;
		Class<? extends Annotation> annotationClass;
	}

}
