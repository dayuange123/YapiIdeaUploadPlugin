package com.qbb.processor;

import com.google.common.base.Strings;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.qbb.dto.*;
import com.qbb.dto.moa.YapiMoaDTO;
import com.qbb.upload.UploadYapi;
import com.qbb.util.DesUtil;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MoaProjectProcessor {
	private static NotificationGroup notificationGroup;

	static {
		notificationGroup = new NotificationGroup("Java2Json.NotificationGroup", NotificationDisplayType.BALLOON, true);
	}

	/**
	 * 处理
	 */
	public void process(AnActionEvent e, ConfigDTO configDTO) {
		List<YapiMoaDTO> dtoList = createYapiMoaDTOList(e);
		if (CollectionUtils.isEmpty(dtoList)) {
			Messages.showInfoMessage("Get method doc empty", "Empty");
			return;
		}

		for (YapiMoaDTO moaDTO : dtoList) {
			try {
				// 上传
				YapiResponse yapiResponse = new UploadYapi().uploadSave(moaDTO.transform2YapiSaveParam(configDTO));
				if (yapiResponse.getErrcode() != 0) {
					Messages.showErrorDialog("上传失败！异常: " + yapiResponse.getErrmsg(), "上传失败！");
				} else {
					String url = configDTO.getYapiUrl() + "/project/" + configDTO.getProjectId() + "/interface/api/cat_" + yapiResponse.getCatId();
					Messages.showInfoMessage("上传成功！接口文档url地址:  " + url, "上传成功！");
				}
			} catch (Exception e1) {
				Messages.showErrorDialog("上传失败！异常:  " + e1, "上传失败！");
			}
		}
	}

	private void notifyError(Project project, String msg) {
		Notification error = notificationGroup.createNotification(msg, NotificationType.ERROR);
		Notifications.Bus.notify(error, project);
	}


	private List<YapiMoaDTO> createYapiMoaDTOList(AnActionEvent e) {
		Editor editor = e.getDataContext().getData(CommonDataKeys.EDITOR);
		PsiFile psiFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
		String selectedText = e.getRequiredData(CommonDataKeys.EDITOR).getSelectionModel().getSelectedText();
		assert editor != null;
		Project project = editor.getProject();
		if (Strings.isNullOrEmpty(selectedText)) {
			notifyError(project, "Please select moa interface or method");
			return null;
		}
		if (Objects.isNull(psiFile)) {
			notifyError(project, "file is null");
			return null;
		}
		PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
		PsiClass selectedClass = (PsiClass) PsiTreeUtil.getContextOfType(referenceAt, new Class[]{PsiClass.class});
		if (Objects.isNull(selectedClass)) {
			notifyError(project, "selectedClass is null");
			return null;
		}

		Predicate<PsiMethod> predicate;
		if (selectedText.equals(selectedClass.getName())) {
			predicate = rec -> !rec.getModifierList().hasModifierProperty("private");
		} else {
			predicate = rec -> rec.getName().equals(selectedText);
		}

		return Arrays.stream(selectedClass.getMethods())
				.filter(predicate)
				.map(rec -> createByClassMethod(rec, selectedClass, project))
				.collect(Collectors.toList());
	}


	private YapiMoaDTO createByClassMethod(PsiMethod psiMethodTarget, PsiClass selectedClass, Project project) {
		YapiMoaDTO yapiMoaDTO = actionPerformed(psiMethodTarget, project);
		if (Objects.nonNull(psiMethodTarget.getDocComment())) {
			yapiMoaDTO.setMenu(DesUtil.getMenu(psiMethodTarget.getDocComment().getText()));
			yapiMoaDTO.setStatus(DesUtil.getStatus(psiMethodTarget.getDocComment().getText()));
		}
		if (Objects.isNull(yapiMoaDTO.getMenu()) && Objects.nonNull(selectedClass.getDocComment())) {
			yapiMoaDTO.setMenu(DesUtil.getMenu(selectedClass.getText()));
		}
		return yapiMoaDTO;
	}


	/**
	 * @param psiMethodTarget
	 * @param project
	 * @return
	 */
	public YapiMoaDTO actionPerformed(PsiMethod psiMethodTarget, Project project) {
		try {
			YapiMoaDTO moaDTO = new YapiMoaDTO();
			moaDTO.setResponse(ObjectParseProcessor.parseResp(project, psiMethodTarget.getReturnType(), psiMethodTarget));
			PsiParameter[] psiParameters = psiMethodTarget.getParameterList().getParameters();
			for (PsiParameter psiParameter : psiParameters) {
				moaDTO.setRequestBody(ObjectParseProcessor.parseParam(project, psiParameter, psiMethodTarget));
			}
			moaDTO.setPath(DesUtil.getPath(psiMethodTarget));
			String referenceDesc = psiMethodTarget.getText().replace("<", "&lt;").replace(">", "&gt;");
			moaDTO.setDesc("<pre><code> " + referenceDesc + " </code></pre>");
			moaDTO.setTitle(DesUtil.getDescription(psiMethodTarget));
			return moaDTO;
		} catch (Exception ex) {
			Notification error = notificationGroup.createNotification("Parse method error", NotificationType.ERROR);
			Notifications.Bus.notify(error, project);
		}
		return null;
	}

}
