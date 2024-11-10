package serverless;
/**
 * Function to increment view count of a short
 */

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import tukano.api.Short;
import utils.Props;
import utils.cache.Cache;
import utils.database.DB;

import java.util.Optional;

public class IncrementShortViews {
    private static final String SHORTID = "shortId";
    private static final String HTTP_TRIGGER_NAME="req";
    private static final String HTTP_FUNCTION_NAME="IncrementShortViews";
    private static final String HTTP_TRIGGER_ROUTE="short/incrementViews/{" + SHORTID + "}";

    private static final boolean cache = true;

    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = HTTP_TRIGGER_NAME,
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_TRIGGER_ROUTE)
            HttpRequestMessage<Optional<String>> request,
            @BindingName(SHORTID) String shortId,
            final ExecutionContext context) {

        Props.load("azurekeys-region.props");
        incrementViews(shortId);

        context.getLogger().info("Updated short view count: " + shortId);
        return request.createResponseBuilder(HttpStatus.OK).build();
    }

    private void incrementViews(String shortId) {
        Short shrt = DB.getOne(shortId, Short.class).value();
        shrt.incrementViews();
        DB.updateOne(shrt);

        if(cache) {
            String key = String.format("short:%s", shortId);
            if(Cache.isCached(key))
                Cache.insertIntoCache(key, shrt);
        }

    }
}
