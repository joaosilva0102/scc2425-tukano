package serverless;
/**
 * Function to upload the top 5 shorts to Tukano Recommends user
 */

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import tukano.api.User;
import tukano.api.Short;
import tukano.impl.JavaShorts;
import utils.cache.Cache;
import utils.database.DB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.*;
import java.util.logging.Logger;

@Path("/shorts/recommendations")
public class TukanoRecommendsResource {

    private static final Logger Log = Logger.getLogger(TukanoRecommendsResource.class.getName());

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Short> tukanoRecommendations() {

        Set<String> shortKeys = Cache.getKeys("short:*").value();
        String cacheKey = "user:Tukano:shorts";
        List<Short> tukanoShorts = Cache.getList(cacheKey, Short.class).value();
        List<Short> shorts = new ArrayList<>();
        User user = new User("Tukano", "12345", "tukano@tukano.com", " Tukano Recomends");
        var result = JavaShorts.getInstance().getShorts(user.getUserId());
        List<String> toDelete = new ArrayList<>();
        try {
            toDelete = result.value();
            Log.info("Result: " + result.value());
        } catch (Exception e) {
            Log.severe("Failed to cast result to List<Short>: " + e.getMessage());
        }
        for (String s : toDelete) {
            Log.info("Deleting short: " + s);
            JavaShorts.getInstance().deleteShort(s, user.getPwd(),
                    new NewCookie.Builder("dummy")
                        .value("42").path("/")
                        .comment("sessionId")
                        .maxAge(200)
                        .secure(false)
                        .httpOnly(true)
                        .build());
        }
        for (Short ts : tukanoShorts) {
            Cache.removeFromCache(ts.getShortId());
        }

        for (String key : shortKeys) {
            try {
                Short s = Cache.getFromCache(key, Short.class).value();
                String newShortId = "tukano+" + s.getShortId();
                Log.info("Short: " + newShortId);
                shorts.add(new Short(newShortId, user.getUserId(), s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getTotalViews()));
//                    shorts.add(new Short(newShortId, newUserId, s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getTotalViews()));
            } catch (Exception e) {
                Log.severe("Error parsing data for key: " + key);
            }
        }

        /*List<Short> recShorts = shorts.stream()
                .sorted(Comparator.comparingInt(Short::getTotalViews).reversed()
                        .thenComparingInt(Short::getTotalLikes).reversed()
                        .thenComparingLong(Short::getTimestamp).reversed())
                .limit(5)
                .toList());*/
        List<Short> recShorts = shorts.stream()
                .sorted((s1, s2) -> {
                    int viewDiff = Integer.compare(s2.getTotalViews(), s1.getTotalViews());
                    if (viewDiff != 0) {
                        return viewDiff;
                    }
                    int likeDiff = Integer.compare(s2.getTotalLikes(), s1.getTotalLikes());
                    if (likeDiff != 0) {
                        return likeDiff;
                    }
                    return Long.compare(s2.getTimestamp(), s1.getTimestamp());
                })
                .limit(5)
                .toList();

        for (Short s : shorts) {
            Log.info("Short: " + s.getShortId() + " " + s.getBlobUrl() + " " + s.getTimestamp() + " " + s.getTotalLikes() + " " + s.getTotalViews());
        }

        for (Short s : recShorts) {
            DB.insertOne(s);
            Log.info("REC Short: " + s.getShortId() + " " + s.getBlobUrl() + " " + s.getTimestamp() + " " + s.getTotalLikes() + " " + s.getTotalViews());
        }

        Log.info("Returning top 5 shorts " + recShorts.size());

        return recShorts;
    }
}
