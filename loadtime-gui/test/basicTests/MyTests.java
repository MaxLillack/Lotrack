package basicTests;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import models.MongoLoader;

import org.junit.Assert;
import org.junit.Test;

import play.libs.Json;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import play.test.WithApplication;
import controllers.Application;
import static play.mvc.Http.Status.OK;

public class MyTests extends WithApplication {
	
	@Test
	public void test1() throws Exception {
		Application.fileTree("ConnectBot");
	}
	
	@Test
	public void test2() throws IOException {
		MongoLoader mongoLoader = new MongoLoader();
		System.out.println(Json.toJson(mongoLoader.getResults("C:\\Users\\Max\\workspace\\connectbot\\src\\org\\connectbot\\ActionBarWrapper.java", "ConnectBot")));
	}
	
	@Test
	public void test3() throws Exception {
		Application.fileTree("Tests");
	}
	
	@Test
	public void test4() throws Exception {
		Application.fileTree("am_ed_importcontacts_10304");
	}
	
	@Test
	public void loadResultTest()
	{
		Map<String, String> requestData = new HashMap<>();
		requestData.put("fileName", "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\fabricate.adligo.org\\src\\org\\adligo\\fabricate\\repository\\DependenciesManager.java");
		requestData.put("project", "adligo");
		
		RequestBuilder request = Helpers.fakeRequest("POST", "/loadResult").bodyForm(requestData);
		
		Result result = Helpers.route(request, 300000l);
		Assert.assertEquals(OK, result.status());
	}
	
}
