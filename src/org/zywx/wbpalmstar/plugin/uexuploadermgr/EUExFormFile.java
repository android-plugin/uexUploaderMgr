package org.zywx.wbpalmstar.plugin.uexuploadermgr;

import java.io.InputStream;

public class EUExFormFile {

	/* 文件名称 */
	public String m_filname;

	private String m_targetAddress;

	private String m_filePath;
	public InputStream m_inputStream;

	public boolean m_isUpLoaded;

	public int state = EUExUploaderMgr.F_FILE_TYPE_CREATE;


	public String getM_filePath() {
		return m_filePath;
	}

	public void setM_filePath(String m_filePath) {
		this.m_filePath = m_filePath;
	}

	public String getM_targetAddress() {
		return m_targetAddress;
	}

	public void setM_targetAddress(String m_targetAddress) {
		this.m_targetAddress = m_targetAddress;
	}

	public EUExFormFile(String inTargetAddress, String contentType) {
		m_targetAddress = inTargetAddress;

	}



	public void setInputStream(InputStream inputStream) {

		m_inputStream = inputStream;

	}

	public String getFilname() {
		return m_filePath.substring(m_filePath.lastIndexOf('/') + 1);
	}
}
