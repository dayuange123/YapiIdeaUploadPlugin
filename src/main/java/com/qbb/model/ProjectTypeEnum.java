package com.qbb.model;


import java.util.Arrays;

public enum ProjectTypeEnum {


	API("api"),
	MOA("moa");

	ProjectTypeEnum(String name) {
		this.name = name;
	}

	private final String name;


	public String getName() {
		return name;
	}


	public static ProjectTypeEnum get(String name) {
		return Arrays.stream(ProjectTypeEnum.values()).filter(projectTypeEnum -> projectTypeEnum.name.equals(name))
				.findFirst()
				.orElse(null);
	}

	public static String[] getAllType() {
		return Arrays.stream(ProjectTypeEnum.values())
				.map(ProjectTypeEnum::getName)
				.toArray(String[]::new);
	}

}
