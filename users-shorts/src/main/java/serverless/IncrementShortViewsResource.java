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

@Path("/shorts/view")
public class IncrementShortViewsResource {
    private static final String SHORTID = "shortId";

    private static final boolean cache = true;

    @POST
    @Path("/{" + SHORTID + "}")
    public void incrementViews(@PathParam(SHORTID) String shortId) {
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
