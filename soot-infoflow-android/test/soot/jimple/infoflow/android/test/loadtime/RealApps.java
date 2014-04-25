/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.android.test.loadtime;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException.Missing;
import com.typesafe.config.ConfigFactory;

import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.android.MaxSetupApplication;
import soot.jimple.infoflow.loadtime.ConstraintShare;
import soot.jimple.infoflow.loadtime.MongoLoader;

public class RealApps {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static TestHelper helper;
	private static MongoLoader mongo;
	
	@BeforeClass
	public static void setup() throws UnknownHostException
	{
		helper = new TestHelper();
		mongo = helper.getMongoLoader();
	}
	
	@AfterClass
	public static void closeMongo()
	{
		helper.close();
	}
	
	private LoadTimeInfoflow analyzeAPKFile(String fileName, boolean enableImplicitFlows, String configName) throws IOException {
		String androidJars = System.getenv("ANDROID_JARS");
		if (androidJars == null)
			throw new RuntimeException("Android JAR dir not set");
		System.out.println("Loading Android.jar files from " + androidJars);

		String droidBenchDir = System.getenv("DROIDBENCH");
		if (droidBenchDir == null)
			droidBenchDir = System.getProperty("DROIDBENCH");
		if (droidBenchDir == null)
			throw new RuntimeException("DroidBench dir not set");		
		System.out.println("Loading DroidBench from " + droidBenchDir);
		
		MaxSetupApplication setupApplication = new MaxSetupApplication(androidJars,
				droidBenchDir + File.separator + fileName);
		setupApplication.setTaintWrapperFile("EasyTaintWrapperSource.txt");
		setupApplication.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
		setupApplication.setEnableImplicitFlows(enableImplicitFlows);
		return setupApplication.runInfoflow(configName);
	}
	
	@Test
	public void VLC1() throws IOException {
		runAndCheck("VLC");
	}
	

	@Test(timeout=300000)
	public void Orbot() throws IOException {
		runAndCheck("Orbot");
	}
	
	private void runAndCheck(String collectionName) throws IOException
	{
		Config conf = ConfigFactory.load().getConfig(collectionName);
		
		mongo.logStart(collectionName);
		
		LoadTimeInfoflow infoflow = analyzeAPKFile(conf.getString("apk"), true, collectionName);
		int offset = helper.getFeatureOffset(conf);
		
		String basePath = null;
		try {
			basePath = conf.getString("srcPath");
		} catch(Missing ex) {
			logger.info("No source for {}", collectionName);
		}
		
		mongo.saveResults(infoflow, collectionName, basePath, offset);
		
		mongo.logEnd(collectionName);
		
		checkResults(conf, collectionName);
	}
	
	@Test
	public void Adblock() throws IOException {
		runAndCheck("Adblock");
	}
	
	@Test
	public void ConnectBot() throws IOException {
		runAndCheck("ConnectBot");
	}
	
	
	@Test(timeout=300000)
	public void Avare() throws IOException {
		runAndCheck("Avare");
	}
	
	@Test(timeout=300000)
	public void AndroidsFortune() throws IOException {
		runAndCheck("AndroidsFortune");
	}
	
	@Test(timeout=300000)
	public void BarcodeScanner() throws IOException {
		runAndCheck("BarcodeScanner");
	}
	
	@Test(timeout=600000)
	public void FBReaderJ() throws IOException {
		runAndCheck("FBReaderJ");
	}
	
	@Test
	@Ignore
	public void OsmAnd() throws IOException {
		runAndCheck("OsmAnd");
	}
	
	@Test(timeout=300000)
	public void Tomdroid() throws IOException {
		runAndCheck("Tomdroid");
	}
	
	@Test(timeout=300000)
	public void Wikipedia() throws IOException {
		runAndCheck("Wikipedia");
	}
	
	@Test(timeout=300000)
	public void zeroxbenchmark() throws IOException {
		runAndCheck("zeroxbenchmark");
	}
	
	@Test(timeout=300000)
	public void systemappmover() throws IOException {
		runAndCheck("systemappmover");
	}
	
	@Test(timeout=300000)
	public void twentyfourhour() throws IOException {
		runAndCheck("twentyfourhour");
	}
	
	@Test(timeout=300000)
	public void fahrplan() throws IOException {
		runAndCheck("fahrplan");
	}
	
	@Test
	public void editor() throws IOException {
		runAndCheck("editor");
	}
	
	@Test(timeout=300000)
	public void atimetracker() throws IOException {
		runAndCheck("atimetracker");
	}
	
	@Test
	public void Vol() throws IOException {
		runAndCheck("Vol");
	}
	
	@Test(timeout=300000)
	public void agit() throws IOException {
		runAndCheck("agit");
	}
	
	@Test(timeout=300000)
	@Ignore
	public void aagtl() throws IOException {
		runAndCheck("aagtl");
	}
	
	@Test(timeout=300000)
	public void paperToss() throws IOException {
		runAndCheck("PaperToss");
	}
	
	@Test
	@Ignore
	public void skype() throws IOException {
		runAndCheck("Skype");
	}
	
	
	@Test
	public void facebook() throws IOException {
		runAndCheck("Facebook");
	}
	
	@Test
	@Ignore
	public void facebookMessenger() throws IOException {
		runAndCheck("FacebookMessenger");
	}
	
	@Test
	public void quizduell() throws IOException {
		runAndCheck("Quizduell");
	}
	
	@Test(timeout=300000)
	public void telegram() throws IOException {
		runAndCheck("Telegram");
	}
	
	@Test
	public void FarmHeroesSaga() throws IOException {
		runAndCheck("FarmHeroesSaga");
	}

	@Test
	public void importcontacts() throws IOException {
		runAndCheck("am_ed_importcontacts_10304");
	}
	
	@Test
	@Ignore
	public void doEvaluation() throws IOException {
		
		for(File file : FileUtils.listFiles(new File("C:\\Users\\Max\\Dropbox\\Uni\\AmAVaG\\Texte\\Tracking Configuration Options\\Evaluation\\apk"), TrueFileFilter.TRUE, TrueFileFilter.TRUE)) {
			String filename = FilenameUtils.getBaseName(file.getName());
			String configName = filename.replace(".", "_");
			boolean skip = false;
			
			if(filename.equals("at.bitfire.davdroid_35")) {
				skip = true;
			}
			
			if(mongo.isDone(configName))
			{
				mongo.skip(configName);
				skip = true;
			}
			
			if(!skip) {
				runAndCheck(configName);
			}
		}
	}
	
	private void checkResults(Config conf, String collectionName)
	{
		List<? extends Config> expectedResults = conf.getConfigList("expectedResults");
		
		for(Config resultForClass : expectedResults) {
			
			String className = resultForClass.getString("className");
			
			List<? extends Config> constraints = resultForClass.getConfigList("constraints");
			for(Config constraintElement : constraints)
			{
				int jimpleLine = constraintElement.getInt("jimpleLine");
				String expectedConstraint = constraintElement.getString("constraint");
				
				String constraint = mongo.getConstraint(collectionName, className, jimpleLine);
				Assert.assertEquals("Line " + jimpleLine, expectedConstraint, constraint);
			}
		}
	}
	
	private Collection<String> evaluationCollections()
	{
		Collection<String> collectionNames = new LinkedList<String>();
		collectionNames.add("am_ed_importcontacts_10304");
		collectionNames.add("app_openconnect_819");
		collectionNames.add("at_bitfire_davdroid_35");
		collectionNames.add("caldwell_ben_bites_4");
		collectionNames.add("com_alfray_timeriffic_10905");
		collectionNames.add("com_amphoras_tpthelper_24");
		collectionNames.add("com_android_inputmethod_latin_4424");
		collectionNames.add("com_androidemu_gba_6");
		collectionNames.add("com_androidemu_gbc_32");
		collectionNames.add("com_androidemu_nes_61");
		collectionNames.add("com_anysoftkeyboard_languagepack_malayalam_2");
		collectionNames.add("com_beem_project_beem_15");
		collectionNames.add("com_brewcrewfoo_performance_4");
		collectionNames.add("com_cepmuvakkit_times_200");
		collectionNames.add("com_cr5315_cfdc_18");
		collectionNames.add("com_danvelazco_fbwrapper_20140104");
		collectionNames.add("com_elsdoerfer_android_autostarts_26");
		collectionNames.add("com_github_grimpy_botifier_14");
		collectionNames.add("com_gluegadget_hndroid_3");
		collectionNames.add("com_googamaphone_typeandspeak_36");
		collectionNames.add("com_google_code_apps2org_200");
		collectionNames.add("com_gs_mobileprint_1");
		collectionNames.add("com_java_SmokeReducer_1");
		collectionNames.add("com_jlyr_41");
		collectionNames.add("com_kai1973i_4");
		collectionNames.add("com_kvance_Nectroid_11");
		collectionNames.add("com_kyakujin_android_tagnotepad_3");
		collectionNames.add("com_mareksebera_simpledilbert_32");
		collectionNames.add("com_mehmetakiftutuncu_eshotroid_6");
		collectionNames.add("com_menny_android_anysoftkeyboard_111");
		collectionNames.add("com_pindroid_56");
		collectionNames.add("com_ridgelineapps_resdicegame_13");
		collectionNames.add("com_roguetemple_hydroid_1500");
		collectionNames.add("com_seafile_seadroid2_15");
		collectionNames.add("com_seavenois_tetris_3");
		collectionNames.add("com_sgr_b2_compass_18");
		collectionNames.add("com_smerty_ham_18");
		collectionNames.add("com_spazedog_mounts2sd_33");
		collectionNames.add("com_sputnik_wispr_147");
		collectionNames.add("com_ten15_diyfish_2");
		collectionNames.add("com_tkjelectronics_balanduino_1200050");
		collectionNames.add("com_traffar_game_of_life_2");
		collectionNames.add("com_vonglasow_michael_satstat_60");
		collectionNames.add("com_xabber_androiddev_81");
		collectionNames.add("com_yubico_yubiclip_2");
		collectionNames.add("com_zola_bmi_1");
		collectionNames.add("damo_three_ie_9");
		collectionNames.add("de_onyxbits_textfiction_6");
		collectionNames.add("de_ub0r_android_websms_connector_gmx_3200000");
		collectionNames.add("edu_killerud_kitchentimer_5");
		collectionNames.add("eu_e43_impeller_8007");
		collectionNames.add("eu_lighthouselabs_obd_reader_10");
		collectionNames.add("eu_siebeck_sipswitch_5");
		collectionNames.add("eu_vranckaert_worktime_270");
		collectionNames.add("fr_gaulupeau_apps_InThePoche_8");
		collectionNames.add("fr_strasweb_asso_2");
		collectionNames.add("fr_xgouchet_texteditor_19");
		collectionNames.add("headrevision_BehatReporter_5");
		collectionNames.add("in_shick_diode_14");
		collectionNames.add("jp_sblo_pandora_aGrep_11");
		collectionNames.add("name_soulayrol_rhaa_sholi_4");
		collectionNames.add("net_bytten_xkcdviewer_32");
		collectionNames.add("net_fred_feedex_41");
		collectionNames.add("net_lardcave_keepassnfc_2");
		collectionNames.add("net_nurik_roman_muzei_1008");
		collectionNames.add("net_oschina_app_18");
		collectionNames.add("net_sf_andhsli_hotspotlogin_20");
		collectionNames.add("net_szym_barnacle_39");
		collectionNames.add("nitezh_ministock_52");
		collectionNames.add("org_adaway_48");
		collectionNames.add("org_adblockplus_android_270");
		collectionNames.add("org_ametro_17");
		collectionNames.add("org_billthefarmer_accordion_101");
		collectionNames.add("org_connectbot_365");
		collectionNames.add("org_dolphinemu_dolphinemu_11");
		collectionNames.add("org_gc_networktester_2");
		collectionNames.add("org_geometerplus_zlibrary_ui_android_108022");
		collectionNames.add("org_jf_Penroser_6");
		collectionNames.add("org_marcus905_wifi_ace_20120115");
		collectionNames.add("org_moparisthebest_appbak_2");
		collectionNames.add("org_petero_droidfish_60");
		collectionNames.add("org_projectmaxs_module_filewrite_15");
		collectionNames.add("org_projectmaxs_module_ringermode_15");
		collectionNames.add("org_recentwidget_6");
		collectionNames.add("org_scoutant_cc_1");
		collectionNames.add("org_servDroid_web_1000300");
		collectionNames.add("org_sixgun_ponyexpress_12");
		collectionNames.add("org_smerty_zooborns_14");
		collectionNames.add("org_sufficientlysecure_localcalendar_6");
		collectionNames.add("org_sufficientlysecure_viewer_2500");
		collectionNames.add("org_tunesremote_253");
		collectionNames.add("pl_net_szafraniec_NFCTagmaker_14");
		collectionNames.add("remuco_client_android_1");
		collectionNames.add("ru_glesik_nostrangersms_141");
		collectionNames.add("se_erikofsweden_findmyphone_12");
		collectionNames.add("se_johanhil_duckduckgo_1");
		collectionNames.add("stericson_busybox_157");
		collectionNames.add("tritop_android_SLWTrafficMeterWidget_2");
		collectionNames.add("tritop_androidSLWCpuWidget_6");
		collectionNames.add("uk_org_cardboardbox_wonderdroid_39");
		return collectionNames;
	}
	
	@Test
	@Ignore
	public void removeEvaluationResults()
	{
		for (String collectionName : evaluationCollections()) {
			mongo.clearResult(collectionName);
		}
		mongo.clearResult("constraintShares");
	}
	
	@Test
	public void getEvaluationResults()
	{	
		Writer writer = null;
		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream("C:\\Users\\Max\\Dropbox\\Uni\\AmAVaG\\Texte\\Tracking Configuration Options\\Evaluation\\quries\\constraintShares.csv"), "utf-8"));
		    writer.write("Name" + "," + "Total" + "," + "Share\n");
		    for (String collectionName : evaluationCollections()) {
				ConstraintShare share = mongo.getConstraintShare(collectionName);
				if(share == null) {
					writer.write(collectionName + "," + "," + "\n");
				} else {
					writer.write(collectionName + "," + share.total + "," + share.share + "\n");
				}
			}
		} catch (IOException ex) {
		  
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
		
		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream("C:\\Users\\Max\\Dropbox\\Uni\\AmAVaG\\Texte\\Tracking Configuration Options\\Evaluation\\quries\\jimpleLineCount.csv"), "utf-8"));
		    writer.write("Name" + "," + "JimpleLines\n");
		    for (String collectionName : evaluationCollections()) {
				int jimpleLineCount = mongo.getJimpleLineCount(collectionName);
				writer.write(collectionName + "," + jimpleLineCount + "\n");
			}
		} catch (IOException ex) {
		  
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
		
		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream("C:\\Users\\Max\\Dropbox\\Uni\\AmAVaG\\Texte\\Tracking Configuration Options\\Evaluation\\quries\\constraints.csv"), "utf-8"));
		    writer.write("Constraint" + "," + "Count\n");
		    
		    Map<String, Long> constraints = new HashMap<String, Long>();
		    
		    for (String collectionName : evaluationCollections()) {
				Map<String, Long> projectConstraints = mongo.getConstraintCount(collectionName);
				
			    for(Entry<String, Long> entry : projectConstraints.entrySet())
			    {
			    	if(!constraints.containsKey(entry.getKey())) {
			    		// initialize with zero
			    		constraints.put(entry.getKey(), 0l);
			    	}
			    	constraints.put(entry.getKey(), constraints.get(entry.getKey()) + entry.getValue());
			    }
			}
		    
		    for(Entry<String, Long> entry : constraints.entrySet())
		    {
		    	writer.write(entry.getKey() + "," + entry.getValue() + "\n");
		    }
		    
		} catch (IOException ex) {
		  
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
		
		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream("C:\\Users\\Max\\Dropbox\\Uni\\AmAVaG\\Texte\\Tracking Configuration Options\\Evaluation\\quries\\constraintPerApp.csv"), "utf-8"));
		    writer.write("Constraint" + "," + "Count\n");
		    
		    Map<String, Integer> constraints = new HashMap<String, Integer>();
		    
		    for (String collectionName : evaluationCollections()) {
				Map<String, Integer> projectConstraints = mongo.getConstraintsPerApp(collectionName);
				
			    for(Entry<String, Integer> entry : projectConstraints.entrySet())
			    {
			    	if(!constraints.containsKey(entry.getKey())) {
			    		// initialize with zero
			    		constraints.put(entry.getKey(), 0);
			    	}
			    	constraints.put(entry.getKey(), constraints.get(entry.getKey()) + entry.getValue());
			    }
			}
		    
		    for(Entry<String, Integer> entry : constraints.entrySet())
		    {
		    	writer.write(entry.getKey() + "," + entry.getValue() + "\n");
		    }
		    
		} catch (IOException ex) {
		  
		} finally {
		   try {writer.close();} catch (Exception ex) {}
		}
	}

}
