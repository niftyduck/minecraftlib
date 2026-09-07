package eu.fbk.iv4xr.minecraftlib;

import eu.iv4xr.framework.exception.Iv4xrError;
import eu.iv4xr.framework.mainConcepts.Iv4xrEnvironment;
import eu.iv4xr.framework.mainConcepts.WorldModel;
import eu.iv4xr.framework.spatial.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * An instance of {@link Iv4xrEnvironment} to connect an aplib agent to a
 * running MineflayerTestbench server instance.
 * 
 * @author Davide Prandi
 */
public class MinecraftEnv extends Iv4xrEnvironment {

	public static final String CMD_OBSERVE = "Observe";

	final String mineflyerTestbenchUrl;
	final HttpClient http;
	final Gson gson = new Gson();

	// store the last level for reset
	private String lastLevelCsv;

	// Counter used as the WorldModel timestamp.
	long tick = 0;

	// Cached tag map from the last build/reset: tag -> position.
	public final Map<String, Vec3> tagPositions = new HashMap<>();
	// Cached tag map from the last build/reset: tag -> entity UUID.
	public final Map<String, String> tagUuids = new HashMap<>();

	// connection
	private final static long connectionTimeout = 60;

	// Default MineflyerTestbench server
	private final static String defaultMineflyerTestbenchServer = "http://localhost:3000";

	/**
	 * Use fafault MineflyerTestbench server URL
	 */
	public MinecraftEnv() {
		this(defaultMineflyerTestbenchServer);
	}

	/**
	 * Constructor accept a string representing the MineflyerTestbench server URL
	 * 
	 * @param mineflyerTestbenchUrl
	 */
	public MinecraftEnv(String mineflyerTestbenchUrl) {
		this.mineflyerTestbenchUrl = mineflyerTestbenchUrl;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectionTimeout)).build();
	}

	/**
	 * Observe the Minecraft world
	 */
	@Override
	public WorldModel observe(String agentId) {
		JsonObject status = getJson("/status");
		instrument(new EnvOperation(agentId, null, CMD_OBSERVE, null, WorldModel.class));
		return StatusToWorldModel.convert(agentId, status, tick++);
	}

	/**
	 * Call the build level service and save the tags
	 * 
	 * @param levelCsv
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public Map<String, Vec3> buildLevel(String levelCsv, int x, int y, int z) {
		JsonObject jmsg = new JsonObject();
		jmsg.addProperty("level_csv", levelCsv);
		jmsg.addProperty("x", x);
		jmsg.addProperty("y", y);
		jmsg.addProperty("z", z);
		JsonObject resp = postJson("/build-level", jmsg);
		cacheTags(resp);
		this.lastLevelCsv = levelCsv;
		return new HashMap<>(tagPositions);
	}

	/**
	 * Reset the world
	 */
	@Override
	public void resetWorker() {
		JsonObject resp = postJson("/reset", new JsonObject());
		cacheTags(resp);
	}

	/////////////////////////////////////////////////////
	///
	/// Minecraft actions
	///
	/////////////////////////////////////////////////////

	/**
	 * Send an action to the testbench and return the action outcome
	 * 
	 * @param agentId
	 * @param targetId
	 * @param action
	 * @return
	 */
	private boolean sendAction(String agentId, String targetId, JsonObject action) {
		Boolean r = (Boolean) sendCommand(agentId, targetId, action.get("name").getAsString(), action, Boolean.class);
		return r == null || r;
	}

	/**
	 * All non-observe commands are testbench actions carrying their full json body
	 * incmd.arg
	 */
	@Override
	protected Object sendCommand_(EnvOperation cmd) {
		JsonObject action = (JsonObject) cmd.arg;
		JsonObject resp = postJson("/action", action);
		if (resp != null && resp.has("result") && !resp.get("result").isJsonNull()) {
			return resp.get("result").getAsBoolean();
		}
		return null;
	}

	/**
	 * Move agentId to tag position within a certain distance
	 * 
	 * @param agentId
	 * @param tag
	 * @param distance
	 * @return
	 */
	public boolean moveTo(String agentId, String tag, Double distance) {
		JsonObject a = action("move_to");
		a.addProperty("target", tag);
		if (distance != null) {
			a.addProperty("distance", distance);
		}
		return sendAction(agentId, tag, a);
	}

	/**
	 * Move to position within a certain distance
	 * 
	 * @param agentId
	 * @param pos
	 * @param distance
	 * @return
	 */
	public boolean moveTo(String agentId, Vec3 pos, Double distance) {
		JsonObject a = withCoords(action("move_to"), pos);
		if (distance != null) {
			a.addProperty("distance", distance);
		}
		return sendAction(agentId, null, a);
	}

	/**
	 * Mine a specific tag
	 * 
	 * @param agentId
	 * @param tag
	 * @return
	 */
	public boolean mine(String agentId, String tag) {
		JsonObject a = action("break");
		a.addProperty("target", tag);
		return sendAction(agentId, tag, a);
	}

	/**
	 * Mine at coordinates
	 * 
	 * @param agentId
	 * @param pos
	 * @return
	 */
	public boolean mine(String agentId, Vec3 pos) {
		return sendAction(agentId, null, withCoords(action("break"), pos));
	}

	/**
	 * Place object with tag
	 * 
	 * @param agentId
	 * @param tag
	 * @param face
	 * @return
	 */
	public boolean place(String agentId, String tag, String face) {
		JsonObject a = action("place");
		a.addProperty("target", tag);
		a.addProperty("face", face);
		return sendAction(agentId, tag, a);
	}

	/**
	 * Select an item
	 * 
	 * @param agentId
	 * @param item
	 * @return
	 */
	public boolean selectItem(String agentId, String item) {
		JsonObject a = action("select");
		a.addProperty("item", item);
		return sendAction(agentId, null, a);
	}

	/**
	 * Attack a target specified by a tag or an uuid
	 * 
	 * @param agentId
	 * @param targetTagOrUuid
	 * @return
	 */
	public boolean attack(String agentId, String targetTagOrUuid) {
		JsonObject a = action("attack");
		a.addProperty("target", targetTagOrUuid);
		return sendAction(agentId, targetTagOrUuid, a);
	}

	/**
	 * Snake
	 * 
	 * @param agentId
	 * @param state
	 * @return
	 */
	public boolean sneak(String agentId, boolean state) {
		JsonObject a = action("sneak");
		a.addProperty("state", state);
		return sendAction(agentId, null, a);
	}

	/**
	 * Pick up loot specified by a tag
	 * 
	 * @param agentId
	 * @param tag
	 * @return
	 */
	public boolean pickUpLoot(String agentId, String tag) {
		JsonObject a = action("pick_up_loot");
		a.addProperty("target", tag);
		return sendAction(agentId, tag, a);
	}

	/**
	 * Left hand click to a target
	 * 
	 * @param agentId
	 * @param tag
	 * @return
	 */
	public boolean click(String agentId, String tag) {
		JsonObject a = action("click");
		a.addProperty("target", tag);
		return sendAction(agentId, tag, a);
	}

	/**
	 * Wait some ticks
	 * 
	 * @param agentId
	 * @param ticks
	 * @return
	 */
	public boolean waitTicks(String agentId, int ticks) {
		JsonObject a = action("wait");
		a.addProperty("ticks", ticks);
		return sendAction(agentId, null, a);
	}

	/**
	 * Use the anvil. Requires two items and and option name for the repaired object
	 * 
	 * @param agentId
	 * @param tag
	 * @param itemOne
	 * @param itemTwo
	 * @param customName
	 * @return
	 */
	public boolean anvil(String agentId, String tag, String itemOne, String itemTwo, String customName) {
		JsonObject a = action("anvil");
		a.addProperty("target", tag);
		if (itemOne != null) {
			a.addProperty("item_one", itemOne);
		}
		if (itemTwo != null) {
			a.addProperty("item_two", itemTwo);
		}
		if (customName != null) {
			a.addProperty("custom_name", customName);
		}
		return sendAction(agentId, tag, a);
	}

	/////////////////////////////////////////////////////
	///
	/// Oracles
	///
	/////////////////////////////////////////////////////

	/**
	 * Check the type of a block with a given tag
	 * 
	 * @param agentId
	 * @param tag
	 * @param expected
	 * @param nbt
	 * @return
	 */
	public boolean checkBlock(String agentId, String tag, String expected, String nbt) {
		JsonObject a = action("check_block");
		a.addProperty("target", tag);
		a.addProperty("expected", expected);
		if (nbt != null) {
			a.addProperty("nbt", nbt);
		}
		return sendAction(agentId, tag, a);
	}

	/**
	 * Check the type of a block at a given position
	 * 
	 * @param agentId
	 * @param pos
	 * @param expected
	 * @param nbt
	 * @return
	 */
	public boolean checkBlock(String agentId, Vec3 pos, String expected, String nbt) {
		JsonObject a = withCoords(action("check_block"), pos);
		a.addProperty("expected", expected);
		if (nbt != null) {
			a.addProperty("nbt", nbt);
		}
		return sendAction(agentId, null, a);
	}

	/**
	 * Check if the correct number of an element is present in the inventory
	 * 
	 * @param agentId
	 * @param item
	 * @param count
	 * @return
	 */
	public boolean checkInventory(String agentId, String item, Integer count) {
		JsonObject a = action("check_inventory");
		a.addProperty("item", item);
		if (count != null) {
			a.addProperty("count", count);
		}
		return sendAction(agentId, null, a);
	}

	/**
	 * Check the health of an entity specified as a tag or an uuid
	 * 
	 * @param agentId
	 * @param targetTagOrUuid
	 * @param health
	 * @return
	 */
	public boolean checkEntity(String agentId, String targetTagOrUuid, Float health) {
		JsonObject a = action("check_entity");
		a.addProperty("target", targetTagOrUuid);
		if (health != null) {
			a.addProperty("health", health);
		}
		return sendAction(agentId, targetTagOrUuid, a);
	}

	/**
	 * Read the current health of a mob via the testbench GET /tags/:uuid route
	 * (backed by getMobHealth in abstraction.ts). Accepts either a tag (resolved
	 * through {@link #tagUuids}) or a raw UUID.
	 *
	 * @param tagOrUuid entity tag (e.g. "zombie") or raw entity UUID
	 * @return the current health, or null if the mob is dead / unknown / unreachable
	 */
	public Float getMobHealth(String tagOrUuid) {
		String uuid = tagUuids.getOrDefault(tagOrUuid, tagOrUuid);
		JsonObject resp = getJson("/tags/" + uuid);
		if (resp == null || !resp.has("health") || resp.get("health").isJsonNull()) {
			return null;
		}
		return resp.get("health").getAsFloat();
	}

	/////////////////////////////////////////////////////
	///
	/// Utilities
	///
	/////////////////////////////////////////////////////

	/**
	 * GEt the position of a tagget object
	 * 
	 * @param tag
	 * @return
	 */
	public Vec3 tagPosition(String tag) {
		return tagPositions.get(tag);
	}

	/**
	 * Save tags
	 * 
	 * @param resp
	 */
	private void cacheTags(JsonObject resp) {
		tagPositions.clear();
		tagUuids.clear();
		if (resp == null || !resp.has("tags") || resp.get("tags").isJsonNull()) {
			return;
		}
		JsonObject tags = resp.getAsJsonObject("tags");
		for (String key : tags.keySet()) {
			JsonObject v = tags.getAsJsonObject(key);
			if (v.has("uuid") && !v.get("uuid").isJsonNull()) {
				tagUuids.put(key, v.get("uuid").getAsString());
			} else if (v.has("x")) {
				tagPositions.put(key, new Vec3((float) v.get("x").getAsDouble(), (float) v.get("y").getAsDouble(),
						(float) v.get("z").getAsDouble()));
			}
		}
	}

	/**
	 * Create action
	 * 
	 * @param name
	 * @return
	 */
	private static JsonObject action(String name) {
		JsonObject a = new JsonObject();
		a.addProperty("name", name);
		return a;
	}

	/**
	 * The testbench action schema takes a location as `target`, which is either a
	 * tag string or a nested {x,y,z} object.
	 * 
	 * @param a Action
	 * @param v 3D vector
	 * @return
	 */
	private static JsonObject withCoords(JsonObject a, Vec3 v) {

		JsonObject target = new JsonObject();
		target.addProperty("x", Math.round(v.x));
		target.addProperty("y", Math.round(v.y));
		target.addProperty("z", Math.round(v.z));
		a.add("target", target);
		return a;
	}

	/**
	 * HTTP Get
	 * 
	 * @param path
	 * @return
	 */
	JsonObject getJson(String path) {
		try {
			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(mineflyerTestbenchUrl + path))
					.timeout(Duration.ofSeconds(connectionTimeout)).GET().build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() >= 300) {
				throw new Iv4xrError("GET " + path + " failed: HTTP " + resp.statusCode() + " " + resp.body());
			}
			return gson.fromJson(resp.body(), JsonObject.class);
		} catch (Iv4xrError e) {
			throw e;
		} catch (Exception e) {
			throw new Iv4xrError("GET " + path + " failed: " + e.getMessage());
		}
	}

	/**
	 * HTTP Post
	 * 
	 * @param path
	 * @param body
	 * @return
	 */
	JsonObject postJson(String path, JsonObject body) {
		try {
			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(mineflyerTestbenchUrl + path))
					.timeout(Duration.ofSeconds(connectionTimeout)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			JsonObject json = resp.body() == null || resp.body().isBlank() ? new JsonObject()
					: gson.fromJson(resp.body(), JsonObject.class);
			if (resp.statusCode() >= 300) {
				throw new Iv4xrError("POST " + path + " failed: HTTP " + resp.statusCode() + " " + resp.body());
			}
			return json;
		} catch (Iv4xrError e) {
			throw e;
		} catch (Exception e) {
			throw new Iv4xrError("POST " + path + " failed: " + e.getMessage());
		}
	}

}
