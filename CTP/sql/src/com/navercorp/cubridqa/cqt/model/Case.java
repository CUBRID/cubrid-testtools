/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package com.navercorp.cubridqa.cqt.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;

import com.navercorp.cubridqa.cqt.console.ConsoleAgent;
import com.navercorp.cubridqa.cqt.console.util.CommonFileUtile;
import com.navercorp.cubridqa.cqt.console.util.PropertiesUtil;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias(value = "case")
public class Case extends Resource {

	private String remark;

	private String description;

	public String idx;

	public static String SQL_CATEGORY = "sql";

	public static String SHELL_CATEGORY = "shell";

	public static String GROOVY_CATEGORY = "groovy";

	private String[] category;

	protected String contextPath;

	public static String[] categorys = new String[] { "sql", "shell", "site", "medium" };

	public static List<String> categoryList = Arrays.asList(categorys);

	public static Map categoryMapping = ArrayUtils.toMap(new String[][] { { SQL_CATEGORY, "sql" }, { SHELL_CATEGORY, "shell" }, { SHELL_CATEGORY, "site" }, { SHELL_CATEGORY, "medium" } });
	private static Map fileTypeMapping = ArrayUtils.toMap(new String[][] { { SQL_CATEGORY, ".sql" }, { SHELL_CATEGORY, ".sh" }, { GROOVY_CATEGORY, ".grv" } });
	private int checkCount = 0;

	private int leaf = -1;

	public boolean isLeaf() {
		if (leaf == -1) {
			leaf = isLeaf(this.name) ? 1 : -1;
		}
		return leaf == 1;
	}

	/**
	 * 
	 * @Title: isLeaf
	 * @Description:Determine does the specified category is the smallest
	 *                        classification.
	 * @param @param path
	 * @param @return
	 * @return boolean
	 * @throws
	 */
	public static boolean isLeaf(String path) {
		boolean rs = false;
		if (path != null) {
			File f = new File(path);
			if (f.isDirectory()) {
				File[] children = f.listFiles();
				Arrays.sort(children);
				for (File child : children) {
					String dirName = child.getAbsolutePath();
					dirName = dirName.substring(dirName.lastIndexOf("\\") + 1);
					if (dirName.indexOf("cases") >= 0) {
						rs = true;
						break;
					}
				}
			}
		}
		return rs;
	}

	public Case() {

	}

	public String getContextPath() {
		return contextPath;
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public String getIdx() {
		return idx;
	}

	public void setIdx(String idx) {
		this.idx = idx;
	}

	/**
	 * 
	 * @Title: getAnswerRate
	 * @Description:Get all answers and the ratio of answers and scenarios.
	 * @param @return
	 * @return String
	 * @throws
	 */
	public String getAnswerRate() {
		Map answers = ConsoleAgent.checkAnswers(name);
		return (String.valueOf(answers.get("answerCount"))) + "/" + (String.valueOf(answers.get("caseCount")));
	}

	/**
	 * 
	 * @Title: getCaseTotal
	 * @Description:Get the number of scenarios.
	 * @param @return
	 * @return String
	 * @throws
	 */
	public String getCaseTotal() {
		Map answers = ConsoleAgent.checkAnswers(name);
		return (String) answers.get("caseCount");
	}

	/**
	 * 
	 * @Title: getTotal
	 * @Description:Get the number of all child scenarios,include the scenarios
	 *                  of child categories.
	 * @param @return
	 * @return int
	 * @throws
	 */
	public int getTotal() {
		Map map = ConsoleAgent.checkAnswers(name);
		return (Integer) map.get("caseCount");

	}

	/**
	 * 
	 * @Title: getAllFiles
	 * @Description:Get all scenarios,include the scenarios of child categories.
	 * @param @return
	 * @return String[]
	 * @throws
	 */
	public String[] getAllFiles() {
		List fs = getSubFiles(name);
		return (String[]) fs.toArray(new String[0]);
	}

	private List<String> getSubFiles(String path) {
		File root = new File(path);
		List rs = new ArrayList();
		if (root.isFile()) {
			for (String categoryString : category) {
				if (root.getAbsolutePath().endsWith((String) fileTypeMapping.get(categoryString))) {
					rs.add(root.getAbsolutePath().replaceAll("\\\\", "/"));
				}
			}
			return rs;
		} else {
			File[] files = root.listFiles();
			Arrays.sort(files);
			for (File f : files) {
				rs.addAll(getSubFiles(f.getAbsolutePath()));
			}
		}
		return rs;
	}

	/**
	 * 
	 * @Title: listSubCategory
	 * @Description:Get all child categories.
	 * @param @return
	 * @return String[]
	 * @throws
	 */
	public String[] listSubCategory() {
		if (isLeaf()) {
			return new String[0];
		}
		File f = new File(name);
		List rs = new ArrayList();
		File[] children = f.listFiles();
		Arrays.sort(children);
		for (File child : children) {
			if (child.isDirectory()) {
				rs.add(child.getAbsolutePath().replaceAll("\\\\", "/"));
			}
		}
		return (String[]) rs.toArray(new String[0]);
	}

	public boolean isFile() {
		return !CommonFileUtile.isDirectory(getName());
	}

	public String getResultPath() {
		return name.substring(0, name.lastIndexOf(".")) + ".result";
	}

	public int getCheckCount() {
		return checkCount;
	}

	public void setCheckCount(int checkCount) {
		this.checkCount = checkCount;
	}

}
