package serverless;

import com.azure.cosmos.*;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import tukano.api.Short;
import utils.Props;
import utils.database.CosmosDB;
import utils.database.DB;

public class UpdateViews {
	private static final String NAME = "userId";
	private static final String SHORT = "shortId";
	private static final String PATH = "shorts/{" + NAME + "}/{" + SHORT + "}";
	private static final String BLOBS_TRIGGER_NAME = "blobFunctionTrigger";
	private static final String BLOBS_FUNCTION_NAME = "UpdateViews";
	private static final String DATA_TYPE = "binary";
	private static final String BLOBSTORE_CONNECTION_ENV = "BlobStoreConnection";

	@FunctionName(BLOBS_FUNCTION_NAME)
	public void updateViews(
			@BlobTrigger(name = BLOBS_TRIGGER_NAME, 
			dataType = DATA_TYPE, path = PATH, 
			connection = BLOBSTORE_CONNECTION_ENV) byte[] content,
			@BindingName("userId") String userId,
			@BindingName("shortId") String shortId,
			final ExecutionContext context) {



		context.getLogger().info(String.format("blobFunctionExample: blob : %s, userId : %s, updated with %d bytes", shortId, userId, content.length));
	}
}
