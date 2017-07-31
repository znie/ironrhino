package org.ironrhino.core.jdbc;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.junit.Test;

import lombok.Data;

public class EntityBeanPropertyRowMapperTest {

	static enum Grade {
		A, B, C;
	}

	static @Data class Person {
		private String name;
		private Date dateOfBirth;
		private Grade grade1;

		@Column(name = "g2")
		@Enumerated(EnumType.STRING)
		private Grade grade2;

	}

	@Test
	public void test() {
		EntityBeanPropertyRowMapper<Person> mapper = new EntityBeanPropertyRowMapper<>(Person.class);
		assertEquals("date_of_birth", mapper.underscoreName("dateOfBirth"));
		assertEquals("g2", mapper.underscoreName("grade2"));
	}

}
