package com.qbb.processor;

import com.google.common.base.Strings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.qbb.builder.KV;
import com.qbb.builder.NormalTypes;
import com.qbb.constant.JavaConstant;
import com.qbb.util.CommonUtil;
import com.qbb.util.DesUtil;
import com.qbb.util.PsiAnnotationSearchUtil;
import com.qbb.util.TypeCheckUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.qbb.builder.NormalTypes.collectTypesPackages;

public class ObjectParseProcessor {


	public static String parseParam(Project project, @Nullable PsiParameter psiParameter, PsiMethod psiMethodTarget) {
		PsiType type = psiParameter.getType();
		try {
			KV<String, Object> pojoJson = getPojoJson(project, type);
			pojoJson.set("description", DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()));
			return pojoJson.toPrettyJson();
		} catch (Exception e) {

		}
		return "";
	}

	public static String parseResp(Project project, @Nullable PsiType psiType, PsiMethod psiMethodTarget) {
		try {
			KV<String, Object> pojoJson = getPojoJson(project, psiType);
			pojoJson.set("description", DesUtil.getReturnDesc(psiMethodTarget));
			return pojoJson.toPrettyJson();
		} catch (Exception e) {

		}
		return "";
	}

	/**
	 * 解析map或集合泛型参数
	 * 需要注意 这里只支持
	 *
	 * @param canonicalText 规范文本
	 * @param project       项目
	 * @param pNames        p名字
	 * @return {@link KV}<{@link String}, {@link Object}>
	 */
	public static KV<String, Object> parseMapOrCollectionGenericType(String canonicalText, Project project, Set<String> pNames) {
		KV<String, Object> listKv = new KV<>();

		if (Objects.isNull(canonicalText)) {
			listKv.set("type", "?");
			return listKv;
		}

		if (NormalTypes.noramlTypesPackages.containsKey(canonicalText)) {
			String[] childTypes = canonicalText.split("\\.");
			listKv.set("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(childTypes[childTypes.length - 1])).orElse(childTypes[childTypes.length - 1]));
		} else if (collectTypesPackages.containsKey(canonicalText)) {
			String[] childTypes = canonicalText.split("\\.");
			listKv.set("type", childTypes[childTypes.length - 1]);
		} else {
			PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(canonicalText, GlobalSearchScope.allScope(project));
			List<String> requiredList = new ArrayList<>();
			KV<String, Object> kvObject = getFields(psiClassChild, project, null, null, requiredList, pNames);
			listKv.set("type", "object");
			listKv.set("properties", kvObject);
			listKv.set("required", requiredList);
		}
		return listKv;
	}

	public static KV<String, Object> getPojoJson(Project project, PsiType psiType) throws RuntimeException {
		KV<String, Object> result = new KV<>();
		if (psiType instanceof PsiPrimitiveType) {
			//如果是基本类型
			result.set("type", psiType.getPresentableText());
			result.set("title", "基本类型");
		} else if (NormalTypes.isNormalType(psiType.getPresentableText())) {
			//如果是包装类型
			result.set("type", psiType.getPresentableText());
			result.set("title", "包装");
		}
		//如果是数组
		else if (TypeCheckUtil.isJavaCollection(psiType, project)) {
			String[] types = psiType.getCanonicalText().split("<");
			String childPackage = types[1].split(">")[0];
			result.set("items", parseMapOrCollectionGenericType(childPackage, project, new HashSet<>()));
			result.set("type", "array");
			result.set("title", "数组");
		}
		//如果是map类型
		else if (TypeCheckUtil.isJavaMap(psiType, project)) {
			result.set(KV.by("type", "object"));
			result.set(KV.by("title", "该参数是一个map"));
			if (((PsiClassReferenceType) psiType).getParameters().length > 1) {
				String keyCanonicalText = ((PsiClassReferenceType) psiType).getParameters()[0].getCanonicalText();
				String valCanonicalText = ((PsiClassReferenceType) psiType).getParameters()[1].getCanonicalText();
				KV<String, Object> keyObjSup = new KV<>();
				keyObjSup.set("mapKey", parseMapOrCollectionGenericType(keyCanonicalText, project, new HashSet<>()));
				keyObjSup.set("mapValue", parseMapOrCollectionGenericType(valCanonicalText, project, new HashSet<>()));
				result.set("properties", keyObjSup);
			} else {
				result.set(KV.by("title", "请完善Map<?,?>"));
			}
		} else {
			String[] types = psiType.getCanonicalText().split("<");
			if (types.length > 1) {
				//如果带范型
				PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(types[0], GlobalSearchScope.allScope(project));
				List<String> requiredList = new ArrayList<>();
				KV<String, Object> kvObject = getFields(psiClassChild, project, types, 1, requiredList, new HashSet<>());
				result.set("type", "object");
				result.set("title", psiType.getPresentableText());
				result.set("required", requiredList);
				result.set("properties", kvObject);
			} else {
				PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(psiType.getCanonicalText(), GlobalSearchScope.allScope(project));
				List<String> requiredList = new ArrayList<>();
				KV<String, Object> kvObject = getFields(psiClassChild, project, null, null, requiredList, new HashSet<>());
				result.set("type", "object");
				result.set("required", requiredList);
				result.set("title", psiType.getPresentableText());
				result.set("properties", kvObject);
			}
		}
		return result;
	}


	/**
	 * 对于普通的class对象处理
	 * 不包括{@link NormalTypes#normalTypes} 、Collection、Map对象
	 *
	 * @param psiClass     psi类
	 * @param project      项目
	 * @param childType    子类型
	 * @param index        指数
	 * @param requiredList 要求列表
	 * @param pNames       p名字
	 * @return {@link KV}<{@link String}, {@link Object}>
	 */
	public static KV<String, Object> getFields(PsiClass psiClass, Project project, String[] childType, Integer index, List<String> requiredList, Set<String> pNames) {
		KV<String, Object> kv = new KV<>();
		if (psiClass == null) {
			return kv;
		}
		if (NormalTypes.genericList.contains(psiClass.getName()) && childType != null && childType.length > index) {
			String child = childType[index].split(">")[0];
			PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
			return getFields(psiClassChild, project, childType, index + 1, requiredList, pNames);
		} else {
			for (PsiField field : psiClass.getAllFields()) {
				if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.Deprecate))) {
					continue;
				}
				//如果是有notnull 和 notEmpty 注解就加入必填
				if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotNull))
						|| Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotEmpty))
						|| Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotBlank))) {
					requiredList.add(field.getName());
				}
				Set<String> pNameList = new HashSet<>(pNames);
				pNameList.add(psiClass.getName());
				getField(field, project, kv, childType, index, pNameList);
			}
		}

		return kv;
	}


	public static void getField(PsiField field, Project project, KV<String, Object> kv, String[] childType, Integer index, Set<String> pNames) {
		if (field.getModifierList().hasModifierProperty("final")) {
			return;
		}
		PsiType type = field.getType();
		String name = field.getName();
		//获得link 备注
		String remark = DesUtil.getFiledDesc(field.getDocComment());
		remark = DesUtil.getLinkRemark(remark, project, field);

		// 如果是基本类型
		if (type instanceof PsiPrimitiveType) {
			kv.set(name, FieldParser.parsePsiPrimitiveType(type.getPresentableText(), remark));
			return;
		}
		//reference Type
		//包装类型
		if (NormalTypes.isNormalType(type.getPresentableText())) {
			kv.set(name, FieldParser.parsePsiPrimitiveType(type.getPresentableText(), remark));
			return;
		}
		//泛型处理
		if (NormalTypes.genericList.contains(type.getPresentableText())) {
			if (childType == null) {
				return;
			}
			KV<String, Object> kv1 = new KV<>();
			String child = childType[index].split(">")[0];
			if ("?".equals(child)) {
				kv1.set(KV.by("type", "?"));
				kv1.set(KV.by("description", Strings.isNullOrEmpty(remark) ? name : remark));
				kv1.set(KV.by("mock", NormalTypes.formatMockType("?", "?")));
			} else if (NormalTypes.isNormalType(child) || NormalTypes.noramlTypesPackages.containsKey(child)) {
				PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
				if (Objects.isNull(psiClassChild)) {
					kv1 = FieldParser.parsePsiPrimitiveType(child, remark);
				} else {
					kv1 = FieldParser.parsePsiPrimitiveType(psiClassChild.getName(), remark);
				}
			} else if (TypeCheckUtil.isJavaCollection(child, project)) {
				String listType = null;
				if (childType.length > index + 1) {
					listType = childType[index + 1].split(">")[0];
				}
				kv1.set("items", parseMapOrCollectionGenericType(listType, project, pNames));
				kv1.set("type", "array");
				kv1.set("title", "数组");
			} else if (TypeCheckUtil.isJavaMap(child, project)) {
				kv1.set(KV.by("type", "object"));
				kv1.set(KV.by("title", "该参数是一个map"));
				String keyType = null;
				String valType = null;
				if (childType.length > index + 1) {
					String[] split = childType[index + 1].split(">")[0].split(",");
					if (split.length >= 2) {
						keyType = split[0];
						valType = split[1];
					}
				}
				if ((Objects.nonNull(keyType))) {
					KV<String, Object> keyObjSup = new KV<>();
					keyObjSup.set("mapKey", parseMapOrCollectionGenericType(keyType, project, new HashSet<>()));
					keyObjSup.set("mapValue", parseMapOrCollectionGenericType(valType, project, new HashSet<>()));
					kv1.set("properties", keyObjSup);
				} else {
					kv1.set(KV.by("title", "请完善Map<?,?>"));
				}
			} else {
				//class type
				kv1.set(KV.by("type", "object"));
				PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
				kv1.set(KV.by("description", Strings.isNullOrEmpty(remark) ? name : remark));
				if (!pNames.contains(psiClassChild.getName())) {
					List<String> requiredList = new ArrayList<>();
					kv1.set(KV.by("properties", getFields(psiClassChild, project, childType, index + 1, requiredList, pNames)));
					kv1.set("required", requiredList);
				} else {
					kv1.set(KV.by("type", psiClassChild.getName()));
				}
			}
			kv.set(name, kv1);

			return;
		}

		if (type instanceof PsiArrayType) {
			//array type
			PsiType deepType = type.getDeepComponentType();
			KV<String, Object> kvlist = new KV<>();
			String deepTypeName = deepType.getPresentableText();
			if (deepType instanceof PsiPrimitiveType) {
				kvlist.set("type", type.getPresentableText());
			} else if (NormalTypes.isNormalType(deepTypeName)) {
				kvlist.set("type", CommonUtil.javaType2JsonType(deepTypeName));
			} else {
				kvlist.set(KV.by("type", "object"));
				PsiClass psiClass = PsiUtil.resolveClassInType(deepType);
				if (Objects.nonNull(psiClass)) {
					if (!pNames.contains(psiClass.getName())) {
						List<String> requiredList = new ArrayList<>();
						kvlist.set("properties", getFields(psiClass, project, null, null, requiredList, pNames));
						kvlist.set("required", requiredList);
					} else {
						kvlist.set(KV.by("type", psiClass.getName()));
					}
				}
			}
			KV<String, Object> kv1 = new KV<>();
			kv1.set(KV.by("type", "array"));
			kv1.set(KV.by("description", remark));
			kv1.set("items", kvlist);
			kv.set(name, kv1);
			return;
		}
		if (TypeCheckUtil.isJavaCollection(type, project)) {
			//List类型
			KV<String, Object> kv1 = new KV<>();
			String[] types = type.getCanonicalText().split("<");
			String childPackage = types[1].split(">")[0];
			kv1.set("items", parseMapOrCollectionGenericType(childPackage, project, new HashSet<>()));
			kv1.set("type", "array");
			kv1.set(KV.by("description", remark));
			kv1.set("title", "数组");
			kv.set(name, kv1);
			return;
		}
		if (TypeCheckUtil.isJavaMap(type, project)) {
			//Map类型
			KV<String, Object> kv1 = new KV<>();
			kv1.set(KV.by("type", "object"));
			kv1.set(KV.by("description", remark + "该参数为map"));
			if (((PsiClassReferenceType) type).getParameters().length > 1) {
				String keyCanonicalText = ((PsiClassReferenceType) type).getParameters()[0].getCanonicalText();
				String valCanonicalText = ((PsiClassReferenceType) type).getParameters()[1].getCanonicalText();
				KV<String, Object> keyObjSup = new KV<>();
				keyObjSup.set("mapKey", parseMapOrCollectionGenericType(keyCanonicalText, project, pNames));
				keyObjSup.set("mapValue", parseMapOrCollectionGenericType(valCanonicalText, project, pNames));
				kv1.set("properties", keyObjSup);
			}
			kv.set(name, kv1);
			return;
		}
		//class type
		kv.set(name, parseJavaClass(type, remark, pNames, project, childType, index));
	}

	private static KV<String, Object> parseJavaClass(PsiType type, String remark, Set<String> pNames, Project project, String[] childType, Integer index) {
		KV<String, Object> kv = new KV<>();
		PsiClass psiClass = PsiUtil.resolveClassInType(type);
		if (Objects.isNull(psiClass)) {
			return kv;
		}
		//枚举
		if (psiClass.isEnum()) {
			kv.set("type", "enum");
			StringBuilder remarkBuilder = new StringBuilder();
			Arrays.stream(psiClass.getAllFields())
					.filter(f -> f instanceof PsiEnumConstant)
					.forEach(rec -> {
						String comment = DesUtil.getFiledDesc(rec.getDocComment());
						comment = Strings.isNullOrEmpty(comment) ? comment : "-" + comment;
						remarkBuilder.append(rec.getName()).append(comment);
						remarkBuilder.append("\n");
					});
			remark = remarkBuilder.toString();
			kv.set("description", remark);
			return kv;
		}
		kv.set(KV.by("type", "object"));
		kv.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : (remark + " ," + psiClass.getName()).trim())));
		//避免死循环
		if (!pNames.contains(((PsiClassReferenceType) type).getClassName())) {
			List<String> requiredList = new ArrayList<>();
			kv.set(KV.by("properties", getFields(PsiUtil.resolveClassInType(type), project, childType, index, requiredList, pNames)));
			kv.set("required", requiredList);
		} else {
			kv.set(KV.by("type", ((PsiClassReferenceType) type).getClassName()));
		}
		return kv;
	}


}
