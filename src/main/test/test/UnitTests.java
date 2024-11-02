package main.test.test;

import main.test.tukano.clients.rest.RestBlobsClient;
import main.test.tukano.clients.rest.RestShortsClient;
import main.test.tukano.clients.rest.RestUsersClient;
import tukano.api.Result;
import tukano.api.User;
import tukano.impl.rest.TukanoRestServer;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

public class UnitTests {
	
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}
	
	public static void main(String[] args ) throws Exception {
		new Thread( () -> {
			try { 
				TukanoRestServer.main( new String[] {} );
			} catch( Exception x ) {
				x.printStackTrace();
			}
		}).start();

		
		Thread.sleep(1000);
		
		var serverURI = String.format("http://localhost:%s/rest", TukanoRestServer.PORT);
		
		var blobs = new RestBlobsClient(serverURI);
		var users = new RestUsersClient( serverURI);
		var shorts = new RestShortsClient(serverURI);

		User u1 = new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales");
		User u2 = new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov");
		User u3 = new User("knuth", "98765", "knuth@stanford.edu", "Donald Knuth");
		User u4 = new User("torvalds", "24680", "linus@linux.org", "Linus Torvalds");
		User u5 = new User("lovelace", "13579", "ada@analyticalengine.com", "Ada Lovelace");
		User u6 = new User("hopper", "11223", "grace@navy.mil", "Grace Hopper");
		User u7 = new User("turing", "33445", "alan@enigma.org", "Alan Turing");

		show(users.createUser(u1)); // 200 OK
		show(users.createUser(u2)); // 200 OK
		show(users.createUser(u3)); // 200 OK
		show(users.createUser(u4)); // 200 OK
		show(users.createUser(u4)); // 409 CONFLICT
		show(users.createUser(u5)); // 200 OK
		show(users.createUser(u6)); // 200 OK
		show(users.createUser(u7)); // 200 OK

		show(users.getUser("wales", "12345")); // 200 OK
		show(users.getUser("turing","33445")); // 200 OK
		show(users.getUser("lovelace", "2468")); // 403 FORBIDDEN
		show(users.getUser("kevin", "2468")); // 404 NOT FOUND
		 
		show(users.updateUser("wales", "12345", new User("wales", "12345", "jimmy@wikipedia.com", "" ) )); // 200 OK
		show(users.updateUser("hopper", "11223", new User("hopper", "98765", "hopper@wikipedia.com", "Hopper Grace" ) )); // 200 OK
		show(users.updateUser("knuth", "11223", new User("knuth", "45678", "", "" ) )); // 403 FORBIDDEN
		show(users.updateUser("kevin", "11223", new User("kevin", "45678", "", "" ) )); // 404 NOT FOUND
		 
		show(users.searchUsers("")); // 200 OK
		show(users.searchUsers("o")); // 200 OK
		show(users.searchUsers("k")); // 200 OK
		show(users.searchUsers("tre")); // 200 OK

		show(users.deleteUser("hopper", "11223")); // 403 FORBIDDEN
		show(users.deleteUser("hopper", "98765")); // 200 OK
		show(users.deleteUser("hopper", "11223")); // 404 NOT FOUND
		show(users.deleteUser("turing", "83657")); // 403 FORBIDDEN
		
		Result<tukano.api.Short> s1, s2;

		show(s2 = shorts.createShort("liskov", "54321"));
		show(shorts.createShort("liskov", "54321"));
		show(s1 = shorts.createShort("wales", "12345"));
		show(shorts.createShort("wales", "12345"));
		show(shorts.createShort("wales", "12345"));
		show(shorts.createShort("wales", "12345"));

		/*var blobUrl = URI.create(s2.value().getBlobUrl());
		System.out.println( "------->" + blobUrl );
		
		var blobId = new File( blobUrl.getPath() ).getName();
		System.out.println( "BlobID:" + blobId );
		
		var token = blobUrl.getQuery().split("=")[1];

		blobs.upload(blobUrl.toString(), randomBytes(100), token);*/

		
		var s2id = s2.value().getShortId();
		
		show(shorts.follow("liskov", "wales", true, "54321")); // 200 OK
		show(shorts.follow("knuth", "wales", true, "98765")); // 200 OK
		show(shorts.follow("knuth", "liskov", true, "98765")); // 200 OK
		show(shorts.follow("knuth", "liskov", true, "98765")); // 409 CONFLICT
		show(shorts.followers("wales", "12345")); // 200 OK
		show(shorts.followers("liskov", "54321")); // 200 OK
		
		show(shorts.like(s2id, "liskov", true, "54321")); // 200 OK
		show(shorts.like(s2id, "liskov", true, "54321")); // 409 CONFLICT
		show(shorts.likes(s2id , "54321")); // 200 OK
		show(shorts.getFeed("liskov", "54321")); // 200 OK
		show(shorts.getShort( s2id )); // 200 OK
		
		show(shorts.getShorts( "wales" ));
		show(shorts.getShorts( "liskov" ));

		show(shorts.followers("wales", "12345"));

		show(shorts.getShort( s2id ));

		show(shorts.deleteShort(s2id, "54321"));
		show(shorts.getShort(s2id));
		show(shorts.likes(s2id, "54321"));

		/*blobs.forEach( b -> {
			var r = b.download(blobId);
			System.out.println( Hex.of(Hash.sha256( bytes )) + "-->" + Hex.of(Hash.sha256( r.value() )));

		});*/
		
		show(users.deleteUser("wales", "12345"));

		// CosmosDBLayer.getInstance().clearAllContainers();
		// Cache.flushAll();
		System.exit(0);
	}
	
	
	private static Result<?> show( Result<?> res ) {
		if( res.isOK() )
			System.out.println("OK: " + res.value() );
		else
			System.err.println("ERROR:" + res.error());
		return res;
		
	}
	
	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);
		
		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));		

		return bb.array();
		
	}
}
