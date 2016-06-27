package com.navercorp.cubridqa.shell.common;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

public class HttpUtil {

	public static String getHtmlSource(String url) throws ParseException, IOException {
		HttpParams httpParams = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, 30000);
		HttpConnectionParams.setSoTimeout(httpParams, 30000);
		HttpClient httpclient = new DefaultHttpClient(httpParams);

		HttpGet httpget = new HttpGet(url);

		HttpResponse response = httpclient.execute(httpget);

		HttpEntity entity = response.getEntity();
		if (entity != null) {
			return (EntityUtils.toString(entity));
		}
		return null;
	}

}
