package basicTests;

import java.io.IOException;
import java.net.UnknownHostException;

import models.MongoLoader;

import org.junit.Test;





import play.libs.Json;
import controllers.Application;


public class MyTests {
	
	@Test
	public void test1() throws UnknownHostException {
		Application.fileTree("ConnectBot");
	}
	
	@Test
	public void test2() throws IOException {
		MongoLoader mongoLoader = new MongoLoader();
		System.out.println(Json.toJson(mongoLoader.getResults("C:\\Users\\Max\\workspace\\connectbot\\src\\org\\connectbot\\ActionBarWrapper.java", "ConnectBot")));
	}
	
}
