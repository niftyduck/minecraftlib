package eu.fbk.iv4xr.minecraftlib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import eu.iv4xr.framework.mainConcepts.WorldEntity;
import eu.iv4xr.framework.mainConcepts.WorldModel;
import eu.iv4xr.framework.spatial.Vec3;

import java.util.HashMap;

/**
 * Provide the utility for translation of the JSON returned by the
 * MineflayerTestbench
 * 
 * @author Davide Prandi
 */
public class StatusToWorldModel {

	private StatusToWorldModel() {
	}

	public static final String AGENT_TYPE = "player";
	public static final String BLOCK_ID_PREFIX = "block:";
	public static final String ENTITY_ID_PREFIX = "entity:";
	public static final String INVENTORY_PROP = "inventory";

	public static final String HEALTH = "health";
	public static final String FOOD = "food";
	public static final String STATUS = "status";
	public static final String LAST_ACTION_RESULT = "lastActionResult";
	public static final String INVENTORY = "inventory";
	/** Monotonic count of the bot's deaths, see {@link MinecraftState#getDeathCount()}. */
	public static final String DEATHS = "deaths";
	
	/**
	 * Covert the MineflyerTestbech json into a iv4xr world model.
	 * 
	 * @param agentId
	 * @param status
	 * @param timestamp
	 * @return
	 */
	public static WorldModel convert(String agentId, JsonObject status, long timestamp) {
		WorldModel wom = new WorldModel();
		wom.agentId = agentId;
		wom.timestamp = timestamp;

		Vec3 pos = vec3(status.getAsJsonObject("position"));
		wom.position = pos;
		wom.velocity = new Vec3(0, 0, 0); // TODO This could be refined
 
		// Agent entity
		WorldEntity agent = new WorldEntity(agentId, AGENT_TYPE, true);
		agent.position = pos;
		agent.timestamp = timestamp;
		if (has(status, HEALTH)) {
			agent.properties.put(HEALTH, (float) status.get(HEALTH).getAsDouble());
		}
		
		if (has(status, FOOD)) {
			agent.properties.put(FOOD, (float) status.get(FOOD).getAsDouble());
		}
		
		if (has(status, STATUS)) {
			agent.properties.put("botStatus", status.get(STATUS).getAsString());
		}

		// Death count. Absent when running against a testbench older than
		// se-fbk/MineflayerTestbench PR #10, in which case the agent's death stays unobservable.
		if (has(status, DEATHS)) {
			agent.properties.put(DEATHS, status.get(DEATHS).getAsInt());
		}
		
        if (has(status, LAST_ACTION_RESULT)) {
            agent.properties.put(LAST_ACTION_RESULT, status.get(LAST_ACTION_RESULT).getAsBoolean());
        }

		// simplify inventory management creating a map
        HashMap<String, Integer> inventory = new HashMap<>();
        if (has(status, "inventory")) {
        	for (JsonElement el : status.getAsJsonArray("inventory")) {
        		JsonObject item = el.getAsJsonObject();
        		String name = item.get("name").getAsString();
        		int count = has(item, "count") ? item.get("count").getAsInt() : 1;
        		inventory.merge(name, count, Integer::sum);
        	}
        }
        
        agent.properties.put(INVENTORY_PROP, inventory);
        wom.elements.put(agentId, agent);
        
        // near blocks
        if (has(status, "nearbyBlocks")) {
            for (JsonElement el : status.getAsJsonArray("nearbyBlocks")) {
            	JsonObject o = el.getAsJsonObject();
            	Vec3 v = vec3(o.getAsJsonObject("position"));
            	String id = BLOCK_ID_PREFIX + coordKey(v);
            	WorldEntity we = new WorldEntity(id, o.get("id").getAsString(), false);
            	we.position = v;
            	we.timestamp = timestamp;
            	wom.elements.put(id, we);            	
            }
        }
        
        // near entities
        if (has(status, "nearbyEntities")) {
        	for (JsonElement el : status.getAsJsonArray("nearbyEntities")) {
        		JsonObject o = el.getAsJsonObject();
        		String name = has(o, "name") ? o.get("name").getAsString() : "jdoe";
        		String uuid = has(o, "uuid") ? o.get("uuid").getAsString() : null;
        		Vec3 v = vec3(o.getAsJsonObject("position"));
        		// if available use uuid else create it 
        		// TODO  evalute if it moves
        		String id = uuid != null ? uuid : ENTITY_ID_PREFIX + name + ":" + coordKey(v);
        		WorldEntity we = new WorldEntity(id, name, true);
        		we.position = v;
        		we.timestamp = timestamp;
        		we.properties.put("name", name);
        		if (uuid != null) {
        			we.properties.put("uuid", uuid);
        		}
        		if (has(o, "id")) {
        			we.properties.put("mcEntityId", o.get("id").getAsInt());
        		}
        		wom.elements.put(id, we);
        	}
        }
        
		return wom;
	}

	/////////////////////////////////////////////////////
	///
	/// Utilities
	///
	/////////////////////////////////////////////////////

	/**
	 * Get a key from 3D coords
	 * @param p
	 * @return
	 */
	static String coordKey(Vec3 p) {
		return Math.round(p.x) + "_" + Math.round(p.y) + "_" + Math.round(p.z);
	}

	/**
	 * Parse json to get 3S coords
	 * @param o
	 * @return
	 */
	static Vec3 vec3(JsonObject o) {
		return new Vec3((float) o.get("x").getAsDouble(), (float) o.get("y").getAsDouble(),
				(float) o.get("z").getAsDouble());
	}

	/**
	 * Check if a key is present in a json object
	 * @param o
	 * @param key
	 * @return
	 */
	static boolean has(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull();
	}

}
