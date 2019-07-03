package com.navercorp.cubridqa.ha_repl;

import com.navercorp.cubridqa.common.CommonUtils;

public class HoldCasCheck {

	private boolean holdCas;
	private boolean switchOn;

	public HoldCasCheck(boolean isHoldCas, boolean isSwitchOn) {
		this.holdCas = isHoldCas;
		this.switchOn = isSwitchOn;
	}

	public static HoldCasCheck checkRaw(String statement) {
		if (statement == null || statement.indexOf("holdcas") == -1) { //quickly check			
			return new HoldCasCheck(false, false);
		}
		statement = CommonUtils.replace(statement, " ", "").trim();
		if (statement.startsWith("--+holdcason")) {
			return new HoldCasCheck(true, true);
		} else if (statement.startsWith("--+holdcasoff")) {
			return new HoldCasCheck(true, false);
		} else {
			return new HoldCasCheck(false, false);
		}
	}

	public static HoldCasCheck check(String statement) {
		if (statement == null || statement.indexOf("$HOLDCAS") == -1) { //quickly check
			return new HoldCasCheck(false, false);
		}
		statement = CommonUtils.replace(statement, " ", "").trim();
		if (statement.startsWith("$HOLDCAS_ON")) {
			return new HoldCasCheck(true, true);
		} else if (statement.startsWith("$HOLDCAS_OFF")) {
			return new HoldCasCheck(true, false);
		} else {
			return new HoldCasCheck(false, false);
		}
	}

	public boolean isHoldCas() {
		return holdCas;
	}

	public boolean isSwitchOn() {
		return switchOn;
	}
}
