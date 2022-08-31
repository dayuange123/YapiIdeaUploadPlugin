package com.qbb.processor;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.qbb.builder.KV;
import com.qbb.builder.NormalTypes;
import com.qbb.constant.SwaggerConstants;
import com.qbb.util.CommonUtil;
import com.qbb.util.PsiAnnotationSearchUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 属性解析器
 *
 * @author liuzhiyuan
 * @date 2022/08/31
 */
public class FieldParser {

	public static KV<String, Object> parsePsiPrimitiveType(String name, String remark) {
		KV<String, Object> kv = new KV<>();
		kv.set("type", CommonUtil.javaType2JsonType(name));
		if (!Strings.isNullOrEmpty(remark)) {
			kv.set("description", remark);
		}
		kv.set("mock", NormalTypes.formatMockType(name));
		return kv;
	}


}
