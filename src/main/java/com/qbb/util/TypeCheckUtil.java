package com.qbb.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Arrays;
import java.util.Objects;

public class TypeCheckUtil {
	public static boolean isJavaMap(PsiType psiType, Project project) {
		String canonicalText = psiType.getCanonicalText().split("<")[0];
		return isJavaMap(canonicalText, project);
	}


	public static boolean isJavaCollection(PsiType psiType, Project project) {
		String canonicalText = psiType.getCanonicalText().split("<")[0];
		return isJavaCollection(canonicalText, project);
	}


	/**
	 * 是java集合
	 *
	 * @param text    类全名
	 * @param project 项目
	 * @return boolean
	 */
	public static boolean isJavaCollection(String text, Project project) {
		PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(text, GlobalSearchScope.allScope(project));
		if (Objects.isNull(psiClassChild)) {
			return false;
		}
		if ("Collection".equals(psiClassChild.getName())) {
			return true;
		}
		PsiClass[] supers = psiClassChild.getSupers();
		return Arrays.stream(supers)
				.anyMatch(rec -> "Collection".equals(rec.getName()));
	}


	public static boolean isJavaMap(String text, Project project) {
		PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(text, GlobalSearchScope.allScope(project));
		if (Objects.isNull(psiClassChild)) {
			return false;
		}
		if ("Map".equals(psiClassChild.getName())) {
			return true;
		}
		PsiClass[] supers = psiClassChild.getSupers();
		return Arrays.stream(supers)
				.anyMatch(rec -> "Map".equals(rec.getName()));
	}
}
