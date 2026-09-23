package eu.fbk.iv4xr.minecraftlib;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.iv4xr.framework.mainConcepts.TestAgent;
import eu.iv4xr.framework.mainConcepts.TestDataCollector;
import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import static nl.uu.cs.aplib.AplibEDSL.SEQ;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.TESTBENCH_URL;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.MINECRAFT_ADDRESS;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.TEST_AGENT;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.assumeTestBenchRunning;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.levelPath;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.runAgent;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.logVerdicts;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.totalVerdicts;
import static eu.fbk.iv4xr.minecraftlib.TestUtils.attackWithSword;


public class MinecraftGoalLibTest {
	
    static final Logger logger = Logger.getLogger(MinecraftGoalLibTest.class.getName());
    
    @BeforeEach
    void requireTestbench() {
    	assumeTestBenchRunning(TESTBENCH_URL);
    }
	
    /**
    * Select an item and check inventory contents and a block type.
    */
    @Test
    @DisplayName("test agent collects PASS verdicts")
    void ccollectVerdictsTest() {
        MinecraftEnv env = new MinecraftEnv(TESTBENCH_URL);
        env.join(MINECRAFT_ADDRESS);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        TestAgent agent = new TestAgent(TEST_AGENT, "tester");
        agent.setTestDataCollector(new TestDataCollector());

        env.buildLevel(levelPath("aplib-demo.csv"), 16, 65, 0);

        GoalStructure G = SEQ(
                goalLib.selected("diamond_pickaxe"),
                goalLib.assertHasItem(agent, "diamond_pickaxe", 1),
                goalLib.assertHasItem(agent, "stone", 64),
                goalLib.assertBlockIs(agent, "target", "diamond_block", null));

        agent.attachState(state).attachEnvironment(env).setGoal(G);
        runAgent(agent, state, G);

        assertTrue(G.getStatus().success(), "top goal should be solved: " + G.getStatus());
        TestDataCollector dc = agent.getTestDataCollector();
        assertEquals(3, dc.getNumberOfPassVerdictsSeen(), "expected 3 passing verdicts");
        assertEquals(0, dc.getNumberOfFailVerdictsSeen(), "expected no failing verdicts");
    }
    
    /**
     * The move-to coordinates. The scenario is a 20x20 diamond base where the agent
     * navigates to each of the four corners by explicit coordinates and
     * checks that each corner is the expected wood type.
     */
    @Test
    @DisplayName("test agent moves to each corner by coordinates and verifies the wood")
    void test_coordinate_move_to_wood_corners() {
        MinecraftEnv env = new MinecraftEnv(TESTBENCH_URL);
        env.join(MINECRAFT_ADDRESS);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        TestAgent agent = new TestAgent(TEST_AGENT, "tester");
        agent.setTestDataCollector(new TestDataCollector());

        // 20x20 diamond base at (0,65,0); wood blocks sit at y=66 on each corner.
        env.buildLevel(levelPath("wood-corners.csv"), 0, 65, 0);

        GoalStructure G = SEQ(
                goalLib.reached(new Vec3(0, 66, 0), 2),
                goalLib.assertBlockIs(agent, new Vec3(0, 66, 0), "oak_log", null),
                goalLib.reached(new Vec3(19, 66, 0), 2),
                goalLib.assertBlockIs(agent, new Vec3(19, 66, 0), "spruce_log", null),
                goalLib.reached(new Vec3(0, 66, 19), 2),
                goalLib.assertBlockIs(agent, new Vec3(0, 66, 19), "birch_log", null),
                goalLib.reached(new Vec3(19, 66, 19), 2),
                goalLib.assertBlockIs(agent, new Vec3(19, 66, 19), "jungle_log", null));

        agent.attachState(state).attachEnvironment(env).setGoal(G);
        runAgent(agent, state, G);

        assertTrue(G.getStatus().success(),
                "agent should reach all four corners and verify their wood: " + G.getStatus());
        TestDataCollector dc = agent.getTestDataCollector();
        assertEquals(4, dc.getNumberOfPassVerdictsSeen(), "expected 4 passing wood-type verdicts");
        assertEquals(0, dc.getNumberOfFailVerdictsSeen(), "expected no failing verdicts");
    }

    /**
     * Replicate anvil bug. The agent combines two iron helmets (with a rename) on an anvil, 
     * wait, then check the inventory for 2 iron helmets.
     *
     * The test is expected to <i>fail</i> on Minecraft vanilla 1.21.x, because combining two
     * helmets on an anvil produces ONE repaired helmet, not two. The test passes by
     * confirming exactly that expected failing verdict, and logs why.
     */
    @Test
    @DisplayName("anvil: combine two iron helmets (equiv. anvil-test.json)")
    void test_anvil_combine_helmets() {
        MinecraftEnv env = new MinecraftEnv(TESTBENCH_URL);
        env.join(MINECRAFT_ADDRESS);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        TestAgent agent = new TestAgent(TEST_AGENT, "tester");
        agent.setTestDataCollector(new TestDataCollector());

        logger.info("anvil-test: the underlying game check is EXPECTED TO FAIL — combining two "
                + "iron_helmets on an anvil yields ONE repaired helmet, not two, so "
                + "check_inventory(iron_helmet, count=2) correctly returns false. This minecraftlib "
                + "test passes by confirming that expected negative verdict.");

        env.buildLevel(levelPath("anvil-test.csv"), 16, 65, 0);

        GoalStructure G = SEQ(
                goalLib.usedAnvil("anvil", "iron_helmet", "iron_helmet", "test"),
                goalLib.waited(20),
                goalLib.assertHasItem(agent, "iron_helmet", 2));

        agent.attachState(state).attachEnvironment(env).setGoal(G);
        runAgent(agent, state, G);

        assertTrue(G.getStatus().success(), "anvil scenario should run to completion: " + G.getStatus());
        TestDataCollector dc = agent.getTestDataCollector();
        logVerdicts("anvil-test", dc);
        // The one inventory oracle is expected to produce a FAILING verdict.
        assertEquals(1, totalVerdicts(dc), "the scenario should collect exactly one inventory verdict");
        assertEquals(0, dc.getNumberOfPassVerdictsSeen(), "the count-2 inventory check is expected NOT to pass");
        assertEquals(1, dc.getNumberOfFailVerdictsSeen(),
                "the count-2 inventory check is expected to FAIL (anvil yields 1 helmet, not 2)");
    }
    
    /**
     * The agent ignites TNT
     * next to entities that sit in water, wait for the explosion, then check that
     * the item-frame, item and painting  still exist.
     *
     * The test reproduces Minecraft  1.21.x bug MC-3697: those entities are destroyed by the
     * explosion despite the surrounding water, so the three existence oracles are
     * expected to fail. The test passes by confirming exactly those expected
     * failing verdicts, and logs why.
     */
    @Test
    @DisplayName("MC-3697: entities in water vs TNT explosion (equiv. MC-3697.json)")
    void test_mc3697_entities_survive_explosion() {
        MinecraftEnv env = new MinecraftEnv(TESTBENCH_URL);
        env.join(MINECRAFT_ADDRESS);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        TestAgent agent = new TestAgent(TEST_AGENT, "tester");
        agent.setTestDataCollector(new TestDataCollector());

        logger.info("MC-3697: the underlying game checks are EXPECTED TO FAIL — this reproduces "
                + "Minecraft bug MC-3697, in which the item_frame (if1), item (i1) and painting (p1) "
                + "are destroyed by the TNT explosion despite the surrounding water, so their "
                + "existence checks correctly return false. This minecraftlib test passes by "
                + "confirming those expected negative verdicts.");

        env.buildLevel(levelPath("MC-3697.csv"), 16, 65, 0);

        GoalStructure G = SEQ(
                goalLib.clicked("tnt"),
                goalLib.waited(80),
                goalLib.assertEntityHealth(agent, "if1", null),
                goalLib.assertEntityHealth(agent, "i1", null),
                goalLib.assertEntityHealth(agent, "p1", null));

        agent.attachState(state).attachEnvironment(env).setGoal(G);
        runAgent(agent, state, G);

        assertTrue(G.getStatus().success(), "MC-3697 scenario should run to completion: " + G.getStatus());
        TestDataCollector dc = agent.getTestDataCollector();
        logVerdicts("MC-3697", dc);
        // All three entity oracles are expected to produce FAILING verdicts (bug reproduced).
        assertEquals(3, totalVerdicts(dc), "the scenario should collect three entity verdicts");
        assertEquals(0, dc.getNumberOfPassVerdictsSeen(), "no entity is expected to survive the blast");
        assertEquals(3, dc.getNumberOfFailVerdictsSeen(),
                "all three entity existence checks are expected to FAIL (MC-3697 bug reproduced)");
    }
    
    
    /**
     * Test attack and damage. An AI-less iron golem (100 HP) sits
     * next to the agent that has five type swords. The agent
     * attacks the golem once with each swordand, and
     * after each hit, checks the golem's health equals the expected
     * value.
     */
    @Test
    @DisplayName("damage: attack iron golem with each sword, verify its health each hit")
    void test_sword_damage_on_iron_golem() {
        MinecraftEnv env = new MinecraftEnv(TESTBENCH_URL);
        env.join(MINECRAFT_ADDRESS);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        TestAgent agent = new TestAgent(TEST_AGENT, "tester");
        agent.setTestDataCollector(new TestDataCollector());

        env.buildLevel(levelPath("damage.csv"), 16, 65, 0);

        GoalStructure G = SEQ(
                // baseline: the golem starts at full health
                goalLib.assertEntityHealth(agent, "mob", 100f),
                attackWithSword(goalLib, agent, "wooden_sword", 96f),
                attackWithSword(goalLib, agent, "stone_sword", 91f),
                attackWithSword(goalLib, agent, "iron_sword", 85f),
                attackWithSword(goalLib, agent, "diamond_sword", 78f),
                attackWithSword(goalLib, agent, "netherite_sword", 70f));

        agent.attachState(state).attachEnvironment(env).setGoal(G);
        runAgent(agent, state, G);

        assertTrue(G.getStatus().success(),
                "the golem should be hit by every sword and end at 70 HP: " + G.getStatus());
        TestDataCollector dc = agent.getTestDataCollector();
        logVerdicts("damage", dc);
        assertEquals(6, dc.getNumberOfPassVerdictsSeen(),
                "baseline + one health check per sword should all pass");
        assertEquals(0, dc.getNumberOfFailVerdictsSeen(), "no health check should fail");
    }


    @Test
    @DisplayName("test agent moves towards the zombie and attacks it with a sword")
    void test_arena_attack_zombie() {
        MinecraftEnv env = new MinecraftEnv(TESTBENCH_URL);
        env.join(MINECRAFT_ADDRESS);
        MinecraftState state = new MinecraftState();
        MinecraftGoalLib goalLib = new MinecraftGoalLib();

        TestAgent agent = new TestAgent(TEST_AGENT, "tester");
        agent.setTestDataCollector(new TestDataCollector());

        // 20x20 diamond base at (0,65,0); wood blocks sit at y=66 on each corner.
        env.buildLevel(levelPath("arena.csv"), 0, 65, 0);

        /*GoalStructure G = SEQ(
                goalLib.reached(new Vec3(0, 66, 0), 2),
                goalLib.assertBlockIs(agent, new Vec3(0, 66, 0), "oak_log", null),
                goalLib.reached(new Vec3(19, 66, 0), 2),
                goalLib.assertBlockIs(agent, new Vec3(19, 66, 0), "spruce_log", null),
                goalLib.reached(new Vec3(0, 66, 19), 2),
                goalLib.assertBlockIs(agent, new Vec3(0, 66, 19), "birch_log", null),
                goalLib.reached(new Vec3(19, 66, 19), 2),
                goalLib.assertBlockIs(agent, new Vec3(19, 66, 19), "jungle_log", null));

        agent.attachState(state).attachEnvironment(env).setGoal(G);
        runAgent(agent, state, G);

        assertTrue(G.getStatus().success(),
                "agent should reach all four corners and verify their wood: " + G.getStatus());
        TestDataCollector dc = agent.getTestDataCollector();
        assertEquals(4, dc.getNumberOfPassVerdictsSeen(), "expected 4 passing wood-type verdicts");
        assertEquals(0, dc.getNumberOfFailVerdictsSeen(), "expected no failing verdicts");*/
    }




}
