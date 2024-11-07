package serverless;
/**
 * Function to upload the top 5 shorts to Tukano Recommends user
 */

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import redis.clients.jedis.Jedis;
import tukano.api.Short;
import tukano.api.User;
import utils.Props;
import com.google.gson.Gson;
import tukano.impl.JavaShorts;
import utils.cache.Cache;
import utils.cache.RedisCache;
import utils.database.DB;

import java.util.*;

import static java.lang.String.format;

public class TukanoRecommends {

    private static final Gson gson = new Gson();
    private static final String HTTP_TRIGGER_NAME="reqShort";
    private static final String HTTP_FUNCTION_NAME="tukanoRecommends";
    private static final String HTTP_TRIGGER_ROUTE="rest/short/tukanoRecommends";

    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = HTTP_TRIGGER_NAME,
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_TRIGGER_ROUTE)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("HTTP trigger to fetch top 5 shorts from Redis Cache");
        Props.load("azurekeys-region.props");
        try (var jedis = RedisCache.getCachePool().getResource()) {
        //try{
            //Props.load("azurekeys-region.props");
            /*String redisHost =  System.getProperty("REDIS_HOSTNAME");
            String redisKey = System.getProperty("REDIS_KEY");
            Jedis jedis = new Jedis(redisHost,6380, true);
            jedis.auth(redisKey);*/
            Set<String> shortKeys = jedis.keys("short:*");
            String cacheKey = format("user:%s:shorts", "Tukano");
            List<Short> tukanoshorts = Cache.getList(cacheKey, Short.class).value();
            List<Short> shorts = new ArrayList<>();
            User user = new User("Tukano", "12345", "tukano@tukano.com", " Tukano Recomends");
            var result = JavaShorts.getInstance().getShorts(user.getUserId());
            List<String> toDelete = new ArrayList<>();
            try {
                toDelete = result.value();
                context.getLogger().info("Result: " + result.value());
            } catch (Exception e) {
                context.getLogger().severe("Failed to cast result to List<Short>: " + e.getMessage());
            }
            for (String s : toDelete){
                context.getLogger().info("Deleting short: " + s);
                JavaShorts.getInstance().deleteShort(s, user.getPwd());
            }
            for(Short ts : tukanoshorts){
                Cache.removeFromCache(ts.getShortId());
                //jedis.del(key);
            }

            for (String key : shortKeys) {
                String value = jedis.get(key);
                try {
                    Short s = gson.fromJson(value, Short.class);
                    String newShortId = "tukano+"+s.getShortId();
                    context.getLogger().info("Short: " + newShortId);
                    shorts.add(new Short(newShortId, user.getUserId(), s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getTotalViews()));
//                    shorts.add(new Short(newShortId, newUserId, s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getTotalViews()));
                } catch (Exception e) {
                    context.getLogger().severe("Error parsing data for key: " + key);
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

            for(Short s : shorts){
                context.getLogger().info("Short: " + s.getShortId() + " " + s.getBlobUrl() + " " + s.getTimestamp() + " " + s.getTotalLikes() + " " + s.getTotalViews());
            }

            for (Short s : recShorts){
                DB.insertOne(s);
                context.getLogger().info("REC Short: " + s.getShortId() + " " + s.getBlobUrl() + " " + s.getTimestamp() + " " + s.getTotalLikes() + " " + s.getTotalViews());
            }

            context.getLogger().info("Returning top 5 shorts " + recShorts.size());

            return request.createResponseBuilder(HttpStatus.OK)
                    .body(gson.toJson(recShorts))
                    .build();

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error accessing Redis data: " + e.getMessage())
                    .build();
        }
    }
}