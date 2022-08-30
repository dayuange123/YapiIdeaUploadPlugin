package com.qbb.dto.moa;

import com.google.common.base.Strings;
import com.qbb.constant.YapiConstant;
import com.qbb.dto.ConfigDTO;
import com.qbb.dto.YapiSaveParam;

import java.io.Serializable;

/**
 * yapi dubbo 对象
 *
 * @author chengsheng@qbb6.com
 * @date 2019/1/31 5:36 PM
 */
public class YapiMoaDTO implements Serializable {
	/**
	 * 路径
	 */
	private String path;

	/**
	 * title
	 */
	private String title;
	/**
	 * 响应
	 */
	private String response;
	/**
	 * 描述
	 */
	private String desc;
	/**
	 * 菜单
	 */
	private String menu;
	/**
	 * 状态
	 */
	private String status;

	private String requestBody;


	public String getRequestBody() {
		return requestBody;
	}

	public void setRequestBody(String requestBody) {
		this.requestBody = requestBody;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}


	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}


	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String getMenu() {
		return menu;
	}

	public void setMenu(String menu) {
		this.menu = menu;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public YapiMoaDTO() {
	}

	public YapiSaveParam transform2YapiSaveParam(ConfigDTO configDTO) {
		YapiSaveParam yapiSaveParam = new YapiSaveParam(
				configDTO.getProjectToken(),
				this.getTitle(),
				this.getPath(),
				this.getRequestBody(),
				this.getResponse(),
				Integer.valueOf(configDTO.getProjectId()),
				configDTO.getYapiUrl(),
				this.getDesc());
		yapiSaveParam.setStatus(this.getStatus());
		if (Strings.isNullOrEmpty(this.getMenu())) {
			yapiSaveParam.setMenu(YapiConstant.menu);
		} else {
			yapiSaveParam.setMenu(this.getMenu());
		}
		return yapiSaveParam;
	}
}
