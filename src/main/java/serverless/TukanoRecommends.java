package serverless;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import redis.clients.jedis.Jedis;
import tukano.api.Short;
import tukano.api.User;
import utils.Props;
import utils.cache.RedisCache;
import com.google.gson.Gson;
import tukano.impl.JavaShorts;
import tukano.impl.JavaUsers;
import utils.database.DB;

import java.util.*;
import java.util.stream.Collectors;

public class TukanoRecommends {

    private static final Gson gson = new Gson();

    @FunctionName("tukanoRecommends")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "reqShort",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "serverless/")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("HTTP trigger to fetch top 5 shorts from Redis Cache");

        //try (var jedis = RedisCache.getCachePool().getResource()) {
        try{
            Props.load("azurekeys-northeurope.props");
            String redisHost =  System.getProperty("REDIS_HOSTNAME");
            String redisKey = System.getProperty("REDIS_KEY");
            Jedis jedis = new Jedis(redisHost,6380, true);
            jedis.auth(redisKey);
            Set<String> shortKeys = jedis.keys("short:*");
            List<Short> shorts = new ArrayList<>();
            User user = new User("TukanoRecomends", "12345", "tukano@tukano.com", " Tukano Recomends");
            List<Short> delete = (List<Short>) JavaShorts.getInstance().getShorts(user.getUserId());
            for (Short s : delete){
                DB.deleteOne(s);
            }

            for (String key : shortKeys) {
                String value = jedis.get(key);
                try {
                    Short s = gson.fromJson(value, Short.class);
                    shorts.add(new Short(s.getShortId(), user.getUserId(), s.getBlobUrl(), s.getTimestamp(), s.getTotalLikes(), s.getTotalViews()));
                } catch (Exception e) {
                    context.getLogger().severe("Error parsing data for key: " + key);
                }
            }

            List<Short> recShorts = shorts.stream()
                    .sorted(Comparator.comparingInt(Short::getTotalViews).reversed()
                            .thenComparingInt(Short::getTotalLikes).reversed()
                            .thenComparingLong(Short::getTimestamp).reversed())
                    .limit(5)
                    .toList();

            for (Short s : recShorts){
                DB.insertOne(s);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .build();

        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error accessing Redis data: " + e.getMessage())
                    .build();
        }
    }
}