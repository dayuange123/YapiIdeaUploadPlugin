package com.qbb.util;

import com.intellij.psi.PsiType;

public class TypeCheckUtil {
	public static boolean isJavaMap(PsiType psiType){
		return psiType.getPresentableText().contains("Map") && psiType.getCanonicalText().split("<").length > 1;
	}


	public static boolean isJavaList(PsiType psiType){
		return (psiType.getPresentableText().contains("List") || psiType.getPresentableText().contains("Set"))
				&& psiType.getCanonicalText().split("<").length > 1;
	}
}
