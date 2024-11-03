package serverless;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import redis.clients.jedis.Jedis;
import tukano.api.Shorts;
import utils.RedisCache;
import tukano.api.Short;

import java.util.*;
import java.util.stream.Collectors;

public class TukanoRecommends {
    private static final String TEXT = "text";
    private static final String HTTP_TRIGGER_NAME="reqShort";
    private static final String HTTP_FUNCTION_NAME="tukanoRecommends";
    private static final String HTTP_TRIGGER_ROUTE="serverless/echo/";
    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = HTTP_TRIGGER_NAME,
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_TRIGGER_ROUTE)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("HTTP trigger to fetch top 5 shorts from Redis Cache");

        List<Map<String, String>> recShorts = new ArrayList<>();

        try (var jedis = RedisCache.getCachePool().getResource()) {

            Set<String> shortKeys = jedis.keys("short:*");

            List<Short> shorts = new ArrayList<>();

            for (String key : shortKeys) {
                Map<String, String> data = jedis.hgetAll(key);

                int totalViews = Integer.parseInt(data.getOrDefault("totalViews", "0"));
                int likes = Integer.parseInt(data.getOrDefault("totalLikes", "0"));
                long timestamp = Long.parseLong(data.getOrDefault("timestamp", "0"));
                String blobUrl = data.getOrDefault("blobUrl", "");
                String ownerId = data.getOrDefault("ownerId", "");
                Short s = new Short(key, ownerId, blobUrl, timestamp, likes);
                s.setTotalviews(totalViews);
                shorts.add(s);
            }

            //gets top 5 shorts based on total views, likes and timestamp
            recShorts = shorts.stream()
                    .sorted(Comparator.comparingInt(Short::getTotalViews).reversed()
                            .thenComparingInt(Short::getTotalLikes).reversed()
                            .thenComparingLong(Short::getTimestamp).reversed())
                    .limit(5)
                    .map(Short::toMap)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            context.getLogger().severe("Error accessing Redis: " + e.getMessage());

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error accessing Redis data")
                    .build();
        }
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(recShorts)
                .build();
    }
}
