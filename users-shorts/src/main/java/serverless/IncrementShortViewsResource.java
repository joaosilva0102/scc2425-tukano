package serverless;
/**
 * Function to increment view count of a short
 */

import jakarta.ws.rs.PathParam;
import utils.cache.Cache;
import utils.database.DB;
import tukano.api.Short;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.logging.Logger;

@Path("views")
public class IncrementShortViewsResource {
    private static final String SHORTID = "shortId";

    private static final boolean cache = true;
    private static final Logger Log = Logger.getLogger(IncrementShortViewsResource.class.getName());

    @POST
    @Path("/{" + SHORTID + "}")
    public void incrementViews(@PathParam(SHORTID) String shortId) {
        Log.info("Incrementing short views: " + shortId);
        Short shrt = DB.getOne(shortId, Short.class).value();
        shrt.incrementViews();
        DB.updateOne(shrt);
        if(cache) {
            String key = String.format("short:%s", shortId);
            if (Cache.isCached(key))
                Cache.insertIntoCache(key, shrt);
        }
    }
}
