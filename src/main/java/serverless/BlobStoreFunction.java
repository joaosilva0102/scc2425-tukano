package serverless;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import utils.database.DB;
import tukano.api.Short;

public class BlobStoreFunction {
	private static final String NAME = "name";
	private static final String PATH = "shorts/{" + NAME + "}";
	private static final String BLOBS_TRIGGER_NAME = "blobFunctionTrigger";
	private static final String BLOBS_FUNCTION_NAME = "blobFunctionExample";
	private static final String DATA_TYPE = "binary";
	private static final String BLOBSTORE_CONNECTION_ENV = "BlobStoreConnection";

	@FunctionName(BLOBS_FUNCTION_NAME)
	public void blobFunctionExample(
			@BlobTrigger(name = BLOBS_TRIGGER_NAME, 
			dataType = DATA_TYPE, path = PATH, 
			connection = BLOBSTORE_CONNECTION_ENV) byte[] content,
			@BindingName("name") String blobname, final ExecutionContext context) {
		byte[] data = content;
		String[] blobTokens = blobname.split("'+'");
		String userId = blobTokens[0];
		String blobId = blobTokens[1];
		Short s = DB.getOne(blobId, Short.class).value();
		System.out.println(s.toString());
		s.incrementViews();
		System.out.println(s.toString());
		DB.updateOne(s);
		context.getLogger().info(String.format("blobFunctionExample: blob : %s, updated with %d bytes", blobname, content.length));
	}
}
