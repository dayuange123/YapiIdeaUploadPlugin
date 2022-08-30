package com.qbb.interaction;

import com.google.common.base.Strings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.qbb.builder.BuildJsonForYapi;
import com.qbb.component.ConfigPersistence;
import com.qbb.constant.YapiConstant;
import com.qbb.dto.*;
import com.qbb.model.ProjectTypeEnum;
import com.qbb.processor.MoaProjectProcessor;
import com.qbb.upload.UploadYapi;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @description: 入口
 * @author: chengsheng@qbb6.com
 * @date: 2019/5/15
 */
public class UploadToYapi extends AnAction {

	private static final MoaProjectProcessor MOA_PROJECT_PROCESSOR = new MoaProjectProcessor();


	private ConfigDTO getConfigDTO(AnActionEvent e, Project project) {
		try {
			List<ConfigDTO> configs = ServiceManager.getService(ConfigPersistence.class).getConfigs();
			if (configs == null || configs.size() == 0) {
				Messages.showErrorDialog("请先去配置界面配置yapi配置", "获取配置失败！");
				return null;
			}
			PsiFile psiFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
			String virtualFile = psiFile.getVirtualFile().getPath();
			List<ConfigDTO> collect = configs.stream()
					.filter(it -> {
						if (!it.getProjectName().equals(project.getName())) {
							return false;
						}
						String str = (File.separator + it.getProjectName() + File.separator) + (it.getModuleName().equals(it.getProjectName()) ? "" : (it.getModuleName() + File.separator));
						return virtualFile.contains(str);
					})
					.collect(Collectors.toList());
			if (collect.isEmpty()) {
				Messages.showErrorDialog("没有找到对应的yapi配置，请在菜单 > Preferences > Other setting > YapiUpload 添加", "Error");
				return null;
			}
			return collect.get(0);

		} catch (Exception e2) {
			Messages.showErrorDialog("获取配置失败，异常:  " + e2.getMessage(), "获取配置失败！");
			return null;
		}
	}

	@Override
	public void actionPerformed(AnActionEvent e) {
		Editor editor = e.getDataContext().getData(CommonDataKeys.EDITOR);

		if (Objects.isNull(editor)) {
			Messages.showErrorDialog("Editor is null", "Error");
			return;
		}
		Project project = editor.getProject();
		// 获取配置
		ConfigDTO configDTO = getConfigDTO(e, project);
		if (Objects.isNull(configDTO)) {
			return;
		}

		// 判断项目类型
		ProjectTypeEnum projectTypeEnum = ProjectTypeEnum.get(configDTO.getProjectType());
		if (Objects.isNull(projectTypeEnum)) {
			Messages.showErrorDialog(" ProjectType null", "获取配置失败");
			return;
		}
		switch (projectTypeEnum) {
			case MOA:
				MOA_PROJECT_PROCESSOR.process(e, configDTO);
				break;
			case API: {
				//获得api 需上传的接口列表 参数对象
				ArrayList<YapiApiDTO> yapiApiDTOS = new BuildJsonForYapi().actionPerformedList(e, configDTO.getAttachUpload(), null);
				if (yapiApiDTOS != null) {
					for (YapiApiDTO yapiApiDTO : yapiApiDTOS) {
						YapiSaveParam yapiSaveParam = new YapiSaveParam(configDTO.getProjectToken(), yapiApiDTO.getTitle(), yapiApiDTO.getPath(), yapiApiDTO.getParams(), yapiApiDTO.getRequestBody(), yapiApiDTO.getResponse(), Integer.valueOf(configDTO.getProjectId()),
								configDTO.getYapiUrl(), true, yapiApiDTO.getMethod(), yapiApiDTO.getDesc(), yapiApiDTO.getHeader());
						yapiSaveParam.setReq_body_form(yapiApiDTO.getReq_body_form());
						yapiSaveParam.setReq_body_type(yapiApiDTO.getReq_body_type());
						yapiSaveParam.setReq_params(yapiApiDTO.getReq_params());
						yapiSaveParam.setStatus(yapiApiDTO.getStatus());
						if (!Strings.isNullOrEmpty(yapiApiDTO.getMenu())) {
							yapiSaveParam.setMenu(yapiApiDTO.getMenu());
						} else {
							yapiSaveParam.setMenu(YapiConstant.menu);
						}
						try {
							// 上传
							YapiResponse yapiResponse = new UploadYapi().uploadSave(yapiSaveParam);
							if (yapiResponse.getErrcode() != 0) {
								Messages.showInfoMessage("上传失败，原因:  " + yapiResponse.getErrmsg(), "上传失败！");
							} else {
								String url = configDTO.getYapiUrl() + "/project/" + configDTO.getProjectId() + "/interface/api/cat_" + yapiResponse.getCatId();
								this.setClipboard(url);
								Messages.showInfoMessage("上传成功！接口文档url地址:  " + url, "上传成功！");
							}
						} catch (Exception e1) {
							Messages.showErrorDialog("上传失败！异常:  " + e1, "上传失败！");
						}
					}
				}
			}
		}

	}

	/**
	 * @description: 设置到剪切板
	 * @param: [content]
	 * @return: void
	 * @author: chengsheng@qbb6.com
	 * @date: 2019/7/3
	 */
	private void setClipboard(String content) {
		//获取系统剪切板
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		//构建String数据类型
		StringSelection selection = new StringSelection(content);
		//添加文本到系统剪切板
		clipboard.setContents(selection, null);
	}
}
