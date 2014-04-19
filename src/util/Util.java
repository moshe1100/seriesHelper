package util;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

public class Util {

	public static boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
	
	public static boolean isDigit(char ch) {
		return ch >= '0' && ch <= '9';
	}

	public static String getCommaSeparated(List<String> lastSortColumnsOrder) {
		StringBuilder builder = new StringBuilder();
		String result = "";
		for (String string : lastSortColumnsOrder) {
			builder.append(string).append(",");
		}
		
		if (builder.length() > 0){
			result = builder.substring(0, builder.length()-1);
		}
		return result;

	}

	public static boolean isEmpty(String text) {
		return text == null || text.isEmpty();
	}
	
	public static BufferedReader getStringAsBufferReader(String str){
		return new BufferedReader(new StringReader(str));
	}

	public static int getOnlyDigitCharsAsNumber(String splitLine) {
		String digitsOnly = "0";
		for (int i = 0; i < splitLine.length(); i++) {
			if (isDigit(splitLine.charAt(i))){
				digitsOnly += splitLine.charAt(i);
			}
		}
		return Integer.parseInt(digitsOnly);
	}
}
