package eu.fbk.iv4xr.minecraftlib;

import static nl.uu.cs.aplib.AplibEDSL.SEQ;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assumptions;

import eu.iv4xr.framework.mainConcepts.TestAgent;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;

/**
 * Shared helper methods for testing Minecraft aplib agents
 */
public class TestUtils {


    private TestUtils() {
    }

	static final String TESTBENCH_URL = "http://localhost:3000";
	static final String MINECRAFT_ADDRESS = "localhost:25565";
	static final String TEST_AGENT = "bot";
	static final int MAX_TICKS = 120;
	
    static final Logger logger = Logger.getLogger(MinecraftGoalLibTest.class.getName());
    
    /**
     * Resolve level path csv file
     * @param csvName
     * @return
     */
    public static String levelPath(String csvName) {
        URL url = TestUtils.class.getResource("/levels/" + csvName);
        if (url == null) {
            throw new IllegalStateException("Level resource not found on classpath: /levels/" + csvName);
        }
        try {
            return new File(url.toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            return new File(url.getPath()).getAbsolutePath();
        }
    }
    
    /**
     * Check if the MinecraftTestbench is reachable
     * @param url
     * @return
     */
    public static boolean isTestBenchRunning(String url) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/status"))
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * JUnit assumption for checking if the MineflyerTestbench is running.
     * If not, log a warning and skip the test.
     * This is to avoid failures due to MineflyerTestbench server not running.
     */
    public static void assumeTestBenchRunning(String url) {
    	boolean running = isTestBenchRunning(url);
        if (!running) {
            logger.warning("MineflayerTestbench not reachable at " + url + ". Skipping tests. ");
        }
        Assumptions.assumeTrue(running,
                "MineflayerTestbench not reachable at " + url + "; skipping.");
    }
    
    
    /**
     * Drive the agent (ticking it) until its top-level goal finishes or
     * MAX_TICKS is reached.
     */
    public static void runAgent(TestAgent agent, MinecraftState state, GoalStructure topGoal) {
        state.updateState(TEST_AGENT);
        int k = 0;
        while (topGoal.getStatus().inProgress() && k < MAX_TICKS) {
            agent.update();
            k++;
        }
    }

    /** 
     * Total number of verdicts (pass + fail) collected by the aplib collector. 
     */
    public static int totalVerdicts(TestDataCollector dc) {
        return dc.getNumberOfPassVerdictsSeen() + dc.getNumberOfFailVerdictsSeen();
    }

    /** 
     * Log the pass/fail verdict breakdown under the given label. 
     */
    public static void logVerdicts(String label, TestDataCollector dc) {
        logger.info(label + " verdicts: " + dc.getNumberOfPassVerdictsSeen() + " pass, "
                + dc.getNumberOfFailVerdictsSeen() + " fail");
    }
    
    
    /**
     * Example of complex goal. The agent select a sword from the inventory,
     * attack a mob and check the expected energy
     */
    public static GoalStructure attackWithSword(MinecraftGoalLib goalLib, TestAgent agent,
            String sword, float expectedMobHealth) {
        return SEQ(
                goalLib.selected(sword),
                goalLib.waited(20),
                goalLib.attacked("mob"),
                goalLib.waited(5),
                goalLib.assertEntityHealth(agent, "mob", expectedMobHealth));
    }
    

}
