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

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;

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

import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException.Missing;
import com.typesafe.config.ConfigFactory;

import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.android.MaxSetupApplication;
import soot.jimple.infoflow.loadtime.MongoLoader;


//@BenchmarkOptions(benchmarkRounds = 5, warmupRounds = 3)
public class Evaluation /*extends AbstractBenchmark*/ {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static TestHelper helper;
	private static MongoLoader mongo;
	
	private static final boolean forceRecalculation = true;
	
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
	
	@Test(timeout = 300000)
	public void am_ed_importcontacts_10304() throws IOException {
		if (forceRecalculation || !mongo.isDone("am_ed_importcontacts_10304")) {
			runAndCheck("am_ed_importcontacts_10304");
		}
	}

	@Test(timeout = 300000)
	
	public void app_openconnect_819() throws IOException {
		if (forceRecalculation || !mongo.isDone("app_openconnect_819")) {
			runAndCheck("app_openconnect_819");
		}
	}

	@Test(timeout = 300000)
	
	public void at_bitfire_davdroid_35() throws IOException {
		if (forceRecalculation || !mongo.isDone("at_bitfire_davdroid_35")) {
			runAndCheck("at_bitfire_davdroid_35");
		}
	}

	@Test(timeout = 300000)
	
	public void caldwell_ben_bites_4() throws IOException {
		if (forceRecalculation || !mongo.isDone("caldwell_ben_bites_4")) {
			runAndCheck("caldwell_ben_bites_4");
		}
	}

	@Test(timeout = 300000)
	
	public void com_alfray_timeriffic_10905() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_alfray_timeriffic_10905")) {
			runAndCheck("com_alfray_timeriffic_10905");
		}
	}

	@Test(timeout = 300000)
	
	public void com_amphoras_tpthelper_24() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_amphoras_tpthelper_24")) {
			runAndCheck("com_amphoras_tpthelper_24");
		}
	}

	@Test(timeout = 300000)
	
	public void com_android_inputmethod_latin_4424() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_android_inputmethod_latin_4424")) {
			runAndCheck("com_android_inputmethod_latin_4424");
		}
	}

	@Test(timeout = 300000)
	
	public void com_androidemu_gba_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_androidemu_gba_6")) {
			runAndCheck("com_androidemu_gba_6");
		}
	}

	@Test(timeout = 300000)
	
	public void com_androidemu_gbc_32() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_androidemu_gbc_32")) {
			runAndCheck("com_androidemu_gbc_32");
		}
	}

	@Test(timeout = 300000)
	public void com_androidemu_nes_61() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_androidemu_nes_61")) {
			runAndCheck("com_androidemu_nes_61");
		}
	}

	@Test(timeout = 300000)
	
	public void com_anysoftkeyboard_languagepack_malayalam_2()
			throws IOException {
		if (forceRecalculation || !mongo.isDone("com_anysoftkeyboard_languagepack_malayalam_2")) {
			runAndCheck("com_anysoftkeyboard_languagepack_malayalam_2");
		}
	}

	@Test(timeout = 300000)
	
	public void com_beem_project_beem_15() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_beem_project_beem_15")) {
			runAndCheck("com_beem_project_beem_15");
		}
	}

	@Test(timeout = 300000)
	
	public void com_brewcrewfoo_performance_4() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_brewcrewfoo_performance_4")) {
			runAndCheck("com_brewcrewfoo_performance_4");
		}
	}

	@Test(timeout = 300000)
	
	public void com_cepmuvakkit_times_200() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_cepmuvakkit_times_200")) {
			runAndCheck("com_cepmuvakkit_times_200");
		}
	}

	@Test(timeout = 300000)
	
	public void com_cr5315_cfdc_18() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_cr5315_cfdc_18")) {
			runAndCheck("com_cr5315_cfdc_18");
		}
	}

	@Test(timeout = 300000)
	
	public void com_danvelazco_fbwrapper_20140104() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_danvelazco_fbwrapper_20140104")) {
			runAndCheck("com_danvelazco_fbwrapper_20140104");
		}
	}

	@Test(timeout = 300000)
	
	public void com_elsdoerfer_android_autostarts_26() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_elsdoerfer_android_autostarts_26")) {
			runAndCheck("com_elsdoerfer_android_autostarts_26");
		}
	}

	@Test(timeout = 300000)
	
	public void com_github_grimpy_botifier_14() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_github_grimpy_botifier_14")) {
			runAndCheck("com_github_grimpy_botifier_14");
		}
	}

	@Test(timeout = 300000)
	
	public void com_gluegadget_hndroid_3() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_gluegadget_hndroid_3")) {
			runAndCheck("com_gluegadget_hndroid_3");
		}
	}

	@Test(timeout = 300000)
	
	public void com_googamaphone_typeandspeak_36() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_googamaphone_typeandspeak_36")) {
			runAndCheck("com_googamaphone_typeandspeak_36");
		}
	}

	@Test(timeout = 300000)
	
	public void com_google_code_apps2org_200() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_google_code_apps2org_200")) {
			runAndCheck("com_google_code_apps2org_200");
		}
	}

	@Test(timeout = 300000)
	
	public void com_gs_mobileprint_1() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_gs_mobileprint_1")) {
			runAndCheck("com_gs_mobileprint_1");
		}
	}

	@Test(timeout = 300000)
	
	public void com_java_SmokeReducer_1() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_java_SmokeReducer_1")) {
			runAndCheck("com_java_SmokeReducer_1");
		}
	}

	@Test(timeout = 300000)
	public void com_jlyr_41() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_jlyr_41")) {
			runAndCheck("com_jlyr_41");
		}
	}

	@Test(timeout = 300000)
	
	public void com_kai1973i_4() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_kai1973i_4")) {
			runAndCheck("com_kai1973i_4");
		}
	}

	@Test(timeout = 300000)
	public void com_kvance_Nectroid_11() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_kvance_Nectroid_11")) {
			runAndCheck("com_kvance_Nectroid_11");
		}
	}

	@Test(timeout = 300000)
	
	public void com_kyakujin_android_tagnotepad_3() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_kyakujin_android_tagnotepad_3")) {
			runAndCheck("com_kyakujin_android_tagnotepad_3");
		}
	}

	@Test(timeout = 300000)
	
	public void com_mareksebera_simpledilbert_32() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_mareksebera_simpledilbert_32")) {
			runAndCheck("com_mareksebera_simpledilbert_32");
		}
	}

	@Test(timeout = 300000)
	
	public void com_mehmetakiftutuncu_eshotroid_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_mehmetakiftutuncu_eshotroid_6")) {
			runAndCheck("com_mehmetakiftutuncu_eshotroid_6");
		}
	}

	@Test(timeout = 300000)
	
	public void com_menny_android_anysoftkeyboard_111() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_menny_android_anysoftkeyboard_111")) {
			runAndCheck("com_menny_android_anysoftkeyboard_111");
		}
	}

	@Test(timeout = 300000)
	
	public void com_pindroid_56() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_pindroid_56")) {
			runAndCheck("com_pindroid_56");
		}
	}

	@Test(timeout = 300000)
	
	public void com_ridgelineapps_resdicegame_13() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_ridgelineapps_resdicegame_13")) {
			runAndCheck("com_ridgelineapps_resdicegame_13");
		}
	}

	@Test(timeout = 300000)
	
	public void com_roguetemple_hydroid_1500() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_roguetemple_hydroid_1500")) {
			runAndCheck("com_roguetemple_hydroid_1500");
		}
	}

	@Test(timeout = 300000)
	public void com_seafile_seadroid2_15() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_seafile_seadroid2_15")) {
			runAndCheck("com_seafile_seadroid2_15");
		}
	}

	@Test(timeout = 300000)
	
	public void com_seavenois_tetris_3() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_seavenois_tetris_3")) {
			runAndCheck("com_seavenois_tetris_3");
		}
	}

	@Test(timeout = 300000)
	
	public void com_sgr_b2_compass_18() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_sgr_b2_compass_18")) {
			runAndCheck("com_sgr_b2_compass_18");
		}
	}

	@Test(timeout = 300000)
	
	public void com_smerty_ham_18() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_smerty_ham_18")) {
			runAndCheck("com_smerty_ham_18");
		}
	}

	@Test(timeout = 300000)
	
	public void com_spazedog_mounts2sd_33() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_spazedog_mounts2sd_33")) {
			runAndCheck("com_spazedog_mounts2sd_33");
		}
	}

	@Test(timeout = 300000)
	
	public void com_sputnik_wispr_147() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_sputnik_wispr_147")) {
			runAndCheck("com_sputnik_wispr_147");
		}
	}

	@Test(timeout = 300000)
	
	public void com_ten15_diyfish_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_ten15_diyfish_2")) {
			runAndCheck("com_ten15_diyfish_2");
		}
	}

	@Test(timeout = 300000)
	
	public void com_tkjelectronics_balanduino_1200050() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_tkjelectronics_balanduino_1200050")) {
			runAndCheck("com_tkjelectronics_balanduino_1200050");
		}
	}

	@Test(timeout = 300000)
	
	public void com_traffar_game_of_life_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_traffar_game_of_life_2")) {
			runAndCheck("com_traffar_game_of_life_2");
		}
	}

	@Test(timeout = 300000)
	
	public void com_vonglasow_michael_satstat_60() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_vonglasow_michael_satstat_60")) {
			runAndCheck("com_vonglasow_michael_satstat_60");
		}
	}

	@Test(timeout = 300000)
	
	public void com_xabber_androiddev_81() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_xabber_androiddev_81")) {
			runAndCheck("com_xabber_androiddev_81");
		}
	}

	@Test(timeout = 300000)
	
	public void com_yubico_yubiclip_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_yubico_yubiclip_2")) {
			runAndCheck("com_yubico_yubiclip_2");
		}
	}

	@Test(timeout = 300000)
	
	public void com_zola_bmi_1() throws IOException {
		if (forceRecalculation || !mongo.isDone("com_zola_bmi_1")) {
			runAndCheck("com_zola_bmi_1");
		}
	}

	@Test(timeout = 300000)
	public void damo_three_ie_9() throws IOException {
		if (forceRecalculation || !mongo.isDone("damo_three_ie_9")) {
			runAndCheck("damo_three_ie_9");
		}
	}

	@Test(timeout = 300000)
	
	public void de_onyxbits_textfiction_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("de_onyxbits_textfiction_6")) {
			runAndCheck("de_onyxbits_textfiction_6");
		}
	}

	@Test(timeout = 300000)
	
	public void de_ub0r_android_websms_connector_gmx_3200000()
			throws IOException {
		if (forceRecalculation || !mongo.isDone("de_ub0r_android_websms_connector_gmx_3200000")) {
			runAndCheck("de_ub0r_android_websms_connector_gmx_3200000");
		}
	}

	@Test(timeout = 300000)
	
	public void edu_killerud_kitchentimer_5() throws IOException {
		if (forceRecalculation || !mongo.isDone("edu_killerud_kitchentimer_5")) {
			runAndCheck("edu_killerud_kitchentimer_5");
		}
	}

	@Test(timeout = 300000)
	
	public void eu_e43_impeller_8007() throws IOException {
		if (forceRecalculation || !mongo.isDone("eu_e43_impeller_8007")) {
			runAndCheck("eu_e43_impeller_8007");
		}
	}

	@Test(timeout = 300000)
	
	public void eu_lighthouselabs_obd_reader_10() throws IOException {
		if (forceRecalculation || !mongo.isDone("eu_lighthouselabs_obd_reader_10")) {
			runAndCheck("eu_lighthouselabs_obd_reader_10");
		}
	}

	@Test(timeout = 300000)
	
	public void eu_siebeck_sipswitch_5() throws IOException {
		if (forceRecalculation || !mongo.isDone("eu_siebeck_sipswitch_5")) {
			runAndCheck("eu_siebeck_sipswitch_5");
		}
	}

	@Test(timeout = 300000)
	
	public void eu_vranckaert_worktime_270() throws IOException {
		if (forceRecalculation || !mongo.isDone("eu_vranckaert_worktime_270")) {
			runAndCheck("eu_vranckaert_worktime_270");
		}
	}

	@Test(timeout = 300000)
	
	public void fr_gaulupeau_apps_InThePoche_8() throws IOException {
		if (forceRecalculation || !mongo.isDone("fr_gaulupeau_apps_InThePoche_8")) {
			runAndCheck("fr_gaulupeau_apps_InThePoche_8");
		}
	}

	@Test(timeout = 300000)
	
	public void fr_strasweb_asso_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("fr_strasweb_asso_2")) {
			runAndCheck("fr_strasweb_asso_2");
		}
	}

	@Test(timeout = 300000)
	
	public void fr_xgouchet_texteditor_19() throws IOException {
		if (forceRecalculation || !mongo.isDone("fr_xgouchet_texteditor_19")) {
			runAndCheck("fr_xgouchet_texteditor_19");
		}
	}

	@Test(timeout = 300000)
	
	public void headrevision_BehatReporter_5() throws IOException {
		if (forceRecalculation || !mongo.isDone("headrevision_BehatReporter_5")) {
			runAndCheck("headrevision_BehatReporter_5");
		}
	}

	@Test(timeout = 300000)
	
	public void in_shick_diode_14() throws IOException {
		if (forceRecalculation || !mongo.isDone("in_shick_diode_14")) {
			runAndCheck("in_shick_diode_14");
		}
	}

	@Test(timeout = 300000)
	
	public void jp_sblo_pandora_aGrep_11() throws IOException {
		if (forceRecalculation || !mongo.isDone("jp_sblo_pandora_aGrep_11")) {
			runAndCheck("jp_sblo_pandora_aGrep_11");
		}
	}

	@Test(timeout = 300000)
	
	public void name_soulayrol_rhaa_sholi_4() throws IOException {
		if (forceRecalculation || !mongo.isDone("name_soulayrol_rhaa_sholi_4")) {
			runAndCheck("name_soulayrol_rhaa_sholi_4");
		}
	}

	@Test(timeout = 300000)
	public void net_bytten_xkcdviewer_32() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_bytten_xkcdviewer_32")) {
			runAndCheck("net_bytten_xkcdviewer_32");
		}
	}

	@Test(timeout = 300000)
	
	public void net_fred_feedex_41() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_fred_feedex_41")) {
			runAndCheck("net_fred_feedex_41");
		}
	}

	@Test(timeout = 300000)
	
	public void net_lardcave_keepassnfc_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_lardcave_keepassnfc_2")) {
			runAndCheck("net_lardcave_keepassnfc_2");
		}
	}

	@Test(timeout = 300000)
	
	public void net_nurik_roman_muzei_1008() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_nurik_roman_muzei_1008")) {
			runAndCheck("net_nurik_roman_muzei_1008");
		}
	}

	@Test(timeout = 300000)
	
	public void net_oschina_app_18() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_oschina_app_18")) {
			runAndCheck("net_oschina_app_18");
		}
	}

	@Test(timeout = 300000)
	
	public void net_sf_andhsli_hotspotlogin_20() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_sf_andhsli_hotspotlogin_20")) {
			runAndCheck("net_sf_andhsli_hotspotlogin_20");
		}
	}

	@Test(timeout = 300000)
	
	public void net_szym_barnacle_39() throws IOException {
		if (forceRecalculation || !mongo.isDone("net_szym_barnacle_39")) {
			runAndCheck("net_szym_barnacle_39");
		}
	}

	@Test(timeout = 300000)
	
	public void nitezh_ministock_52() throws IOException {
		if (forceRecalculation || !mongo.isDone("nitezh_ministock_52")) {
			runAndCheck("nitezh_ministock_52");
		}
	}

	@Test(timeout = 300000)
	public void org_adaway_48() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_adaway_48")) {
			runAndCheck("org_adaway_48");
		}
	}

	@Test(timeout = 300000)
	
	public void org_adblockplus_android_270() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_adblockplus_android_270")) {
			runAndCheck("org_adblockplus_android_270");
		}
	}

	@Test(timeout = 300000)
	public void org_ametro_17() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_ametro_17")) {
			runAndCheck("org_ametro_17");
		}
	}

	@Test(timeout = 300000)
	
	public void org_billthefarmer_accordion_101() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_billthefarmer_accordion_101")) {
			runAndCheck("org_billthefarmer_accordion_101");
		}
	}

	@Test(timeout = 300000)
	
	public void org_connectbot_365() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_connectbot_365")) {
			runAndCheck("org_connectbot_365");
		}
	}

	@Test(timeout = 300000)
	
	public void org_dolphinemu_dolphinemu_11() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_dolphinemu_dolphinemu_11")) {
			runAndCheck("org_dolphinemu_dolphinemu_11");
		}
	}

	@Test(timeout = 300000)
	
	public void org_gc_networktester_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_gc_networktester_2")) {
			runAndCheck("org_gc_networktester_2");
		}
	}

	@Test(timeout = 300000)
	
	public void org_geometerplus_zlibrary_ui_android_108022()
			throws IOException {
		if (forceRecalculation || !mongo.isDone("org_geometerplus_zlibrary_ui_android_108022")) {
			runAndCheck("org_geometerplus_zlibrary_ui_android_108022");
		}
	}

	@Test(timeout = 300000)
	
	public void org_jf_Penroser_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_jf_Penroser_6")) {
			runAndCheck("org_jf_Penroser_6");
		}
	}

	@Test(timeout = 300000)
	
	public void org_marcus905_wifi_ace_20120115() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_marcus905_wifi_ace_20120115")) {
			runAndCheck("org_marcus905_wifi_ace_20120115");
		}
	}

	@Test(timeout = 300000)
	
	public void org_moparisthebest_appbak_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_moparisthebest_appbak_2")) {
			runAndCheck("org_moparisthebest_appbak_2");
		}
	}

	@Test(timeout = 300000)
	
	public void org_petero_droidfish_60() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_petero_droidfish_60")) {
			runAndCheck("org_petero_droidfish_60");
		}
	}

	@Test(timeout = 300000)
	public void org_projectmaxs_module_filewrite_15() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_projectmaxs_module_filewrite_15")) {
			runAndCheck("org_projectmaxs_module_filewrite_15");
		}
	}

	@Test(timeout = 300000)
	public void org_projectmaxs_module_ringermode_15() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_projectmaxs_module_ringermode_15")) {
			runAndCheck("org_projectmaxs_module_ringermode_15");
		}
	}

	@Test(timeout = 300000)
	
	public void org_recentwidget_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_recentwidget_6")) {
			runAndCheck("org_recentwidget_6");
		}
	}

	@Test(timeout = 300000)
	
	public void org_scoutant_cc_1() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_scoutant_cc_1")) {
			runAndCheck("org_scoutant_cc_1");
		}
	}

	@Test(timeout = 300000)
	
	public void org_servDroid_web_1000300() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_servDroid_web_1000300")) {
			runAndCheck("org_servDroid_web_1000300");
		}
	}

	@Test(timeout = 300000)
	
	public void org_sixgun_ponyexpress_12() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_sixgun_ponyexpress_12")) {
			runAndCheck("org_sixgun_ponyexpress_12");
		}
	}

	@Test(timeout = 300000)
	
	public void org_smerty_zooborns_14() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_smerty_zooborns_14")) {
			runAndCheck("org_smerty_zooborns_14");
		}
	}

	@Test(timeout = 300000)
	public void org_sufficientlysecure_localcalendar_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_sufficientlysecure_localcalendar_6")) {
			runAndCheck("org_sufficientlysecure_localcalendar_6");
		}
	}

	@Test(timeout = 300000)
	public void org_sufficientlysecure_viewer_2500() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_sufficientlysecure_viewer_2500")) {
			runAndCheck("org_sufficientlysecure_viewer_2500");
		}
	}

	@Test(timeout = 300000)
	public void org_tunesremote_253() throws IOException {
		if (forceRecalculation || !mongo.isDone("org_tunesremote_253")) {
			runAndCheck("org_tunesremote_253");
		}
	}

	@Test(timeout = 300000)
	public void pl_net_szafraniec_NFCTagmaker_14() throws IOException {
		if (forceRecalculation || !mongo.isDone("pl_net_szafraniec_NFCTagmaker_14")) {
			runAndCheck("pl_net_szafraniec_NFCTagmaker_14");
		}
	}

	@Test(timeout = 300000)
	public void remuco_client_android_1() throws IOException {
		if (forceRecalculation || !mongo.isDone("remuco_client_android_1")) {
			runAndCheck("remuco_client_android_1");
		}
	}

	@Test(timeout = 300000)
	public void ru_glesik_nostrangersms_141() throws IOException {
		if (forceRecalculation || !mongo.isDone("ru_glesik_nostrangersms_141")) {
			runAndCheck("ru_glesik_nostrangersms_141");
		}
	}

	@Test(timeout = 300000)
	public void se_erikofsweden_findmyphone_12() throws IOException {
		if (forceRecalculation || !mongo.isDone("se_erikofsweden_findmyphone_12")) {
			runAndCheck("se_erikofsweden_findmyphone_12");
		}
	}

	@Test(timeout = 300000)
	public void se_johanhil_duckduckgo_1() throws IOException {
		if (forceRecalculation || !mongo.isDone("se_johanhil_duckduckgo_1")) {
			runAndCheck("se_johanhil_duckduckgo_1");
		}
	}

	@Test(timeout = 300000)
	public void stericson_busybox_157() throws IOException {
		if (forceRecalculation || !mongo.isDone("stericson_busybox_157")) {
			runAndCheck("stericson_busybox_157");
		}
	}

	@Test(timeout = 300000)
	public void tritop_android_SLWTrafficMeterWidget_2() throws IOException {
		if (forceRecalculation || !mongo.isDone("tritop_android_SLWTrafficMeterWidget_2")) {
			runAndCheck("tritop_android_SLWTrafficMeterWidget_2");
		}
	}

	@Test(timeout = 300000)
	public void tritop_androidSLWCpuWidget_6() throws IOException {
		if (forceRecalculation || !mongo.isDone("tritop_androidSLWCpuWidget_6")) {
			runAndCheck("tritop_androidSLWCpuWidget_6");
		}
	}

	@Test(timeout = 300000)
	public void uk_org_cardboardbox_wonderdroid_39() throws IOException {
		if (forceRecalculation || !mongo.isDone("uk_org_cardboardbox_wonderdroid_39")) {
			runAndCheck("uk_org_cardboardbox_wonderdroid_39");
		}
	}



}
