package com.qbb.processor;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.qbb.builder.KV;
import com.qbb.builder.NormalTypes;
import com.qbb.constant.JavaConstant;
import com.qbb.constant.SwaggerConstants;
import com.qbb.util.DesUtil;
import com.qbb.util.PsiAnnotationSearchUtil;
import com.qbb.util.TypeCheckUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.qbb.builder.NormalTypes.collectTypes;
import static com.qbb.builder.NormalTypes.collectTypesPackages;

public class ObjectParseProcessor {


	public static String parseParam(Project project, @Nullable PsiParameter psiParameter, PsiMethod psiMethodTarget) {
		PsiType type = psiParameter.getType();
		try {
			KV<String, Object> pojoJson = getPojoJson(project, type);
			pojoJson.set("description", DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()));
			return pojoJson.toPrettyJson();
		}catch (Exception e){

		}
		return "";
	}

	public static String parseResp(Project project, @Nullable PsiType psiType, PsiMethod psiMethodTarget) {
		try {
			KV<String, Object> pojoJson = getPojoJson(project, psiType);
			pojoJson.set("description", DesUtil.getReturnDesc(psiMethodTarget));
			return pojoJson.toPrettyJson();
		}catch (Exception e){

		}
		return "";
	}

	public static KV getByCanonicalText(String canonicalText, Project project) {

		KV listKv = new KV();
		if (NormalTypes.noramlTypesPackages.keySet().contains(canonicalText)) {
			String[] childTypes = canonicalText.split("\\.");
			listKv.set("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(childTypes[childTypes.length - 1])).orElse(childTypes[childTypes.length - 1]));
		} else if (collectTypesPackages.containsKey(canonicalText)) {
			String[] childTypes = canonicalText.split("\\.");
			listKv.set("type", childTypes[childTypes.length - 1]);
		} else {
			PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(canonicalText, GlobalSearchScope.allScope(project));
			List<String> requiredList = new ArrayList<>();
			KV kvObject = getFields(psiClassChild, project, null, null, requiredList, new HashSet<>());
			listKv.set("type", "object");

			listKv.set("properties", kvObject);
			listKv.set("required", requiredList);
		}
		return listKv;
	}

	public static KV<String, Object> getPojoJson(Project project, PsiType psiType) throws RuntimeException {
		KV result = new KV();
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
		else if (TypeCheckUtil.isJavaList(psiType)) {
			String[] types = psiType.getCanonicalText().split("<");
			String childPackage = types[1].split(">")[0];
			result.set("items", getByCanonicalText(childPackage, project));
			result.set("type", "array");
			result.set("title", "数组");
		}
		//如果是map类型
		else if (TypeCheckUtil.isJavaMap(psiType)) {
			result.set(KV.by("type", "object"));
			result.set(KV.by("title", "该参数是一个map"));
			if (((PsiClassReferenceType) psiType).getParameters().length > 1) {
				String keyCanonicalText = ((PsiClassReferenceType) psiType).getParameters()[0].getCanonicalText();
				String valCanonicalText = ((PsiClassReferenceType) psiType).getParameters()[1].getCanonicalText();
				KV keyObjSup = new KV();
				keyObjSup.set("mapKey", getByCanonicalText(keyCanonicalText, project));
				keyObjSup.set("mapValue", getByCanonicalText(valCanonicalText, project));
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
				KV kvObject = getFields(psiClassChild, project, types, 1, requiredList, new HashSet<>());
				result.set("type", "object");
				result.set("title", psiType.getPresentableText());
				result.set("required", requiredList);
				result.set("properties", kvObject);
			} else {
				PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(psiType.getCanonicalText(), GlobalSearchScope.allScope(project));
				List<String> requiredList = new ArrayList<>();
				KV kvObject = getFields(psiClassChild, project, null, null, requiredList, new HashSet<>());
				result.set("type", "object");
				result.set("required", requiredList);
				result.set("title", psiType.getPresentableText());
				result.set("properties", kvObject);
			}
		}
		return result;
	}


	public static KV getFields(PsiClass psiClass, Project project, String[] childType, Integer index, List<String> requiredList, Set<String> pNames) {
		KV kv = KV.create();
		if (psiClass != null) {
			if (Objects.nonNull(psiClass.getSuperClass()) && Objects.nonNull(collectTypes.get(psiClass.getSuperClass().getName()))) {
				for (PsiField field : psiClass.getFields()) {
					if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.Deprecate))) {
						continue;
					}
					//如果是有notnull 和 notEmpty 注解就加入必填
					if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotNull))
							|| Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotEmpty))
							|| Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotBlank))) {
						requiredList.add(field.getName());
					}
					Set<String> pNameList = new HashSet<>();
					pNameList.addAll(pNames);
					pNameList.add(psiClass.getName());
					getField(field, project, kv, childType, index, pNameList);
				}
			} else {
				if (NormalTypes.genericList.contains(psiClass.getName()) && childType != null && childType.length > index) {
					String child = childType[index].split(">")[0];
					PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
//					getFilePath(project, filePaths, Arrays.asList(psiClassChild));
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
						Set<String> pNameList = new HashSet<>();
						pNameList.addAll(pNames);
						pNameList.add(psiClass.getName());
						getField(field, project, kv, childType, index, pNameList);
					}
				}
			}
		}
		return kv;
	}


	public static void getField(PsiField field, Project project, KV kv, String[] childType, Integer index, Set<String> pNames) {
		if (field.getModifierList().hasModifierProperty("final")) {
			return;
		}
		PsiType type = field.getType();
		String name = field.getName();
		String remark = "";
		//swagger支持
		remark = StringUtils.defaultIfEmpty(PsiAnnotationSearchUtil.getPsiParameterAnnotationValue(field, SwaggerConstants.API_MODEL_PROPERTY), "");
		if (field.getDocComment() != null) {
			if (Strings.isNullOrEmpty(remark)) {
				remark = DesUtil.getFiledDesc(field.getDocComment());
			}
			//获得link 备注
			remark = DesUtil.getLinkRemark(remark, project, field);
//			getFilePath(project, filePaths, DesUtil.getFieldLinks(project, field));
		}


		// 如果是基本类型
		if (type instanceof PsiPrimitiveType) {
			JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(type.getPresentableText())).orElse(type.getPresentableText()));
			if (!Strings.isNullOrEmpty(remark)) {
				jsonObject.addProperty("description", remark);
			}
			jsonObject.add("mock", NormalTypes.formatMockType(type.getPresentableText()
					, PsiAnnotationSearchUtil.getPsiParameterAnnotationParam(field, SwaggerConstants.API_MODEL_PROPERTY, "example")));
			kv.set(name, jsonObject);
		} else {
			//reference Type
			String fieldTypeName = type.getPresentableText();
			//normal Type
			if (NormalTypes.isNormalType(fieldTypeName)) {
				JsonObject jsonObject = new JsonObject();
				jsonObject.addProperty("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(fieldTypeName)).orElse(fieldTypeName));
				if (!Strings.isNullOrEmpty(remark)) {
					jsonObject.addProperty("description", remark);
				}
				jsonObject.add("mock", NormalTypes.formatMockType(type.getPresentableText()
						, PsiAnnotationSearchUtil.getPsiParameterAnnotationParam(field, SwaggerConstants.API_MODEL_PROPERTY, "example")));
				kv.set(name, jsonObject);
			} else if (!(type instanceof PsiArrayType) && ((PsiClassReferenceType) type).resolve().isEnum()) {
				JsonObject jsonObject = new JsonObject();
				jsonObject.addProperty("type", "enum");
				if (Strings.isNullOrEmpty(remark)) {
					PsiField[] fields = ((PsiClassReferenceType) type).resolve().getAllFields();
					List<PsiField> fieldList = Arrays.stream(fields).filter(f -> f instanceof PsiEnumConstant).collect(Collectors.toList());
					StringBuilder remarkBuilder = new StringBuilder();
					for (PsiField psiField : fieldList) {
						String comment = DesUtil.getFiledDesc(psiField.getDocComment());
						comment = Strings.isNullOrEmpty(comment) ? comment : "-" + comment;
						remarkBuilder.append(psiField.getName()).append(comment);
						remarkBuilder.append("\n");
					}
					remark = remarkBuilder.toString();
				}
				jsonObject.addProperty("description", remark);
				kv.set(name, jsonObject);
			} else if (NormalTypes.genericList.contains(fieldTypeName)) {
				if (childType != null) {
					String child = childType[index].split(">")[0];
					if ("?".equals(child)) {
						KV kv1 = new KV();
						kv.set(name, kv1);
						kv1.set(KV.by("type", "?"));
						kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? name : remark)));
						kv1.set(KV.by("mock", NormalTypes.formatMockType("?", "?")));
					} else if (child.contains("java.util.List") || child.contains("java.util.Set") || child.contains("java.util.HashSet")) {
						index = index + 1;
						PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childType[index].split(">")[0], GlobalSearchScope.allScope(project));
						getCollect(kv, psiClassChild.getName(), remark, psiClassChild, project, name, pNames, childType, index + 1);
					} else if (NormalTypes.isNormalType(child) || NormalTypes.noramlTypesPackages.containsKey(child)) {
						KV kv1 = new KV();
						PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
						kv1.set(KV.by("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(psiClassChild.getName())).orElse(psiClassChild.getName())));
						kv.set(name, kv1);
						kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? name : remark)));
						kv1.set(KV.by("mock", NormalTypes.formatMockType(child
								, PsiAnnotationSearchUtil.getPsiParameterAnnotationParam(field, SwaggerConstants.API_MODEL_PROPERTY, "example"))));
					} else {
						//class type
						KV kv1 = new KV();
						kv1.set(KV.by("type", "object"));
						PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
						kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClassChild.getName().trim()) : remark + " ," + psiClassChild.getName().trim())));
						if (!pNames.contains(psiClassChild.getName())) {
							List<String> requiredList = new ArrayList<>();
							kv1.set(KV.by("properties", getFields(psiClassChild, project, childType, index + 1, requiredList, pNames)));
							kv1.set("required", requiredList);
//							addFilePaths(filePaths, psiClassChild);
						} else {
							kv1.set(KV.by("type", psiClassChild.getName()));
						}
						kv.set(name, kv1);
					}
				}
			} else if (type instanceof PsiArrayType) {
				//array type
				PsiType deepType = type.getDeepComponentType();
				KV kvlist = new KV();
				String deepTypeName = deepType.getPresentableText();
				String cType = "";
				if (deepType instanceof PsiPrimitiveType) {
					kvlist.set("type", type.getPresentableText());
					if (!Strings.isNullOrEmpty(remark)) {
						kvlist.set("description", remark);
					}
				} else if (NormalTypes.isNormalType(deepTypeName)) {
					kvlist.set("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(deepTypeName)).orElse(deepTypeName));
					if (!Strings.isNullOrEmpty(remark)) {
						kvlist.set("description", remark);
					}
				} else {
					kvlist.set(KV.by("type", "object"));
					PsiClass psiClass = PsiUtil.resolveClassInType(deepType);
					cType = psiClass.getName();
					kvlist.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : remark + " ," + psiClass.getName().trim())));
					if (!pNames.contains(PsiUtil.resolveClassInType(deepType).getName())) {
						List<String> requiredList = new ArrayList<>();
						kvlist.set("properties", getFields(psiClass, project, null, null, requiredList, pNames));
						kvlist.set("required", requiredList);
//						addFilePaths(filePaths, psiClass);
					} else {
						kvlist.set(KV.by("type", PsiUtil.resolveClassInType(deepType).getName()));
					}
				}
				KV kv1 = new KV();
				kv1.set(KV.by("type", "array"));
				kv1.set(KV.by("description", (remark + " :" + cType).trim()));
				kv1.set("items", kvlist);
				kv.set(name, kv1);
			} else if (fieldTypeName.startsWith("List") || fieldTypeName.startsWith("Set") || fieldTypeName.startsWith("HashSet")) {
				//list type
				PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
				PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
				if (Objects.nonNull(iterableClass)) {
					String classTypeName = iterableClass.getName();
					getCollect(kv, classTypeName, remark, iterableClass, project, name, pNames, childType, index);
				}
			} else if (fieldTypeName.startsWith("HashMap") || fieldTypeName.startsWith("Map") || fieldTypeName.startsWith("LinkedHashMap")) {
				//HashMap or Map
				KV kv1 = new KV();
				kv1.set(KV.by("type", "object"));
				kv1.set(KV.by("description", remark + "(该参数为map)"));
				if (((PsiClassReferenceType) type).getParameters().length > 1) {
					KV keyObj = new KV();
					keyObj.set("type", "object");
					keyObj.set("description", ((PsiClassReferenceType) type).getParameters()[1].getPresentableText());
					keyObj.set("properties", getFields(PsiUtil.resolveClassInType(((PsiClassReferenceType) type).getParameters()[1]), project, childType, index, new ArrayList<>(), pNames));

					KV key = new KV();
					key.set("type", "object");
					key.set("description", ((PsiClassReferenceType) type).getParameters()[0].getPresentableText());

					KV keyObjSup = new KV();
					keyObjSup.set("mapKey", key);
					keyObjSup.set("mapValue", keyObj);
					kv1.set("properties", keyObjSup);
				} else {
					kv1.set(KV.by("description", "请完善Map<?,?>"));
				}
				kv.set(name, kv1);
			} else {
				//class type
				KV kv1 = new KV();
				PsiClass psiClass = PsiUtil.resolveClassInType(type);
				kv1.set(KV.by("type", "object"));
				kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : (remark + " ," + psiClass.getName()).trim())));
				if (!pNames.contains(((PsiClassReferenceType) type).getClassName())) {
//					addFilePaths(filePaths, psiClass);
					List<String> requiredList = new ArrayList<>();
					kv1.set(KV.by("properties", getFields(PsiUtil.resolveClassInType(type), project, childType, index, requiredList, pNames)));
					kv1.set("required", requiredList);
				} else {
					kv1.set(KV.by("type", ((PsiClassReferenceType) type).getClassName()));
				}
				kv.set(name, kv1);
			}
		}
	}


	public static void getCollect(KV kv, String classTypeName, String remark, PsiClass psiClass, Project project, String name, Set<String> pNames, String[] childType, Integer index) {
		KV kvlist = new KV();
		if (NormalTypes.isNormalType(classTypeName) || collectTypes.containsKey(classTypeName)) {
			kvlist.set("type", Optional.ofNullable(NormalTypes.java2JsonTypes.get(classTypeName)).orElse(classTypeName));
			if (!Strings.isNullOrEmpty(remark)) {
				kvlist.set("description", remark);
			}
		} else {
			kvlist.set(KV.by("type", "object"));
			kvlist.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : remark + " ," + psiClass.getName().trim())));
			if (!pNames.contains(psiClass.getName())) {
				List<String> requiredList = new ArrayList<>();
				kvlist.set("properties", getFields(psiClass, project, childType, index, requiredList, pNames));
				kvlist.set("required", requiredList);
//				addFilePaths(filePaths, psiClass);
			} else {
				kvlist.set(KV.by("type", psiClass.getName()));
			}
		}
		KV kv1 = new KV();
		kv1.set(KV.by("type", "array"));
		kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : remark + " ," + psiClass.getName().trim())));
		kv1.set("items", kvlist);
		kv.set(name, kv1);
	}
}
