package com.qbb.util;

import com.intellij.psi.PsiType;
import com.qbb.builder.NormalTypes;

import java.util.Optional;

public class CommonUtil {

	public static String javaType2JsonType(String javaType){
		return Optional.ofNullable(NormalTypes.java2JsonTypes.get(javaType))
				.orElse(javaType);
	}
}
