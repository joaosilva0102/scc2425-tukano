package main.test.clients;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;

import tukano.api.Result;
import tukano.api.User;
import tukano.impl.rest.TukanoRestServer;

public class Test_JS {

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

		show(users.createUser(u6)); // 200 OK

		Result<tukano.api.Short> s1, s2, s3, s4, s5, s6;

		show(s1 = shorts.createShort("liskov", "54321"));
		show(s2 = shorts.createShort("liskov", "54321"));
		show(s3 = shorts.createShort("wales", "12345"));
		show(s4 = shorts.createShort("wales", "12345"));
		show(s5 = shorts.createShort("wales", "12345"));
		show(s6 = shorts.createShort("wales", "12345"));

		var blobUrl = URI.create(s2.value().getBlobUrl());
		System.out.println( "------->" + blobUrl );

		var blobId = new File( blobUrl.getPath() ).getName();
		System.out.println( "BlobID:" + blobId );

		var token = blobUrl.getQuery().split("=")[1];

		blobs.upload(blobUrl.toString(), randomBytes(100), token);


		var s1id = s1.value().getShortId();
		var s2id = s2.value().getShortId();
		var s3id = s3.value().getShortId();
		var s4id = s4.value().getShortId();
		var s5id = s5.value().getShortId();
		var s6id = s6.value().getShortId();

		System.out.println("Follow: liskov -> wales | Expected result: OK");
		show(shorts.follow("liskov", "wales", true, "54321")); // 200 OK
		System.out.println("Follow: wales -> liskov | Expected result: OK");
		show(shorts.follow("wales", "liskov", true, "12345")); // 200 OK
		System.out.println("Follow: knuth -> wales | Expected result: OK");
		show(shorts.follow("knuth", "wales", true, "98765")); // 200 OK
		System.out.println("Follow: knuth -> liskov | Expected result: OK");
		show(shorts.follow("knuth", "liskov", true, "98765")); // 200 OK
		System.out.println("Follow: knuth -> liskov | Expected result: CONFLICT");
		show(shorts.follow("knuth", "liskov", true, "98765")); // 409 CONFLICT

		System.out.println("Followers: wales | Expected result: OK");
		show(shorts.followers("wales", "12345")); // 200 OK
		System.out.println("Followers: liskov | Expected result: OK");
		show(shorts.followers("liskov", "54321")); // 200 OK

		System.out.println("Like: liskov -> s2 | Expected result: OK");
		show(shorts.like(s2id, "liskov", true, "54321")); // 200 OK
		System.out.println("Like: liskov -> s1 | Expected result: OK");
		show(shorts.like(s1id, "liskov", true, "54321")); // 200 OK
		System.out.println("Like: wales -> s2 | Expected result: OK");
		show(shorts.like(s2id, "wales", true, "12345")); // 200 OK
		System.out.println("Like: wales -> s4 | Expected result: OK");
		show(shorts.like(s4id, "wales", true, "12345")); // 200 OK
		System.out.println("Likes: s1 | Expected result: OK");
		show(shorts.likes(s1id, "54321"));
		System.out.println("Likes: s2 | Expected result: OK");
		show(shorts.likes(s2id , "54321")); // 200 OK
		System.out.println("Likes: s4 | Expected result: OK");
		show(shorts.likes(s4id, "12345"));

		System.out.println("Feed: liskov | Expected result: OK");
		show(shorts.getFeed("liskov", "54321")); // 200 OK

		System.out.println("Short: s2 | Expected result: OK");
		show(shorts.getShort( s2id )); // 200 OK

		System.out.println("Shorts: wales | Expected result: OK");
		show(shorts.getShorts( "wales" ));
		System.out.println("Shorts: liskov | Expected result: OK");
		show(shorts.getShorts( "liskov" ));

		System.out.println("Unfollow: knuth -> wales | Expected result: OK");
		show(shorts.follow("knuth", "wales", false, "98765")); // 200 OK
		System.out.println("Followers: wales | Expected result: OK");
		show(shorts.followers("wales", "12345"));

		System.out.println("Short: s2 | Expected result: OK");
		show(shorts.getShort( s2id ));

		System.out.println("Feed: wales | Expected result: OK");
		show(shorts.getFeed( "wales", "12345" ));
		System.out.println("DeleteShort: s2 | Expected result: OK");
		show(shorts.deleteShort(s2id, "54321"));
		System.out.println("Short: s2 | Expected result: NOT FOUND");
		show(shorts.getShort(s2id));
		System.out.println("Likes: s2 | Expected result: NOT FOUND");
		show(shorts.likes(s2id, "54321"));
		System.out.println("Feed: wales | Expected result: OK");
		show(shorts.getFeed( "wales", "12345" ));

		/*blobs.forEach( b -> {
			var r = b.download(blobId);
			System.out.println( Hex.of(Hash.sha256( bytes )) + "-->" + Hex.of(Hash.sha256( r.value() )));

		});*/
		System.out.println("Likes: s4 | Expected result: OK");
		show(shorts.likes(s4id, "12345"));
		System.out.println("Feed: liskov | Expected result: OK");
		show(shorts.getFeed( "liskov", "54321" ));
		System.out.println("DeleteUser: wales | Expected result: OK");
		show(users.deleteUser("wales", "12345"));
		System.out.println("Shorts: wales | Expected result: NOT FOUND");
		show(shorts.getShorts( "wales" ));
		System.out.println("Followers: wales | Expected result: NOT FOUND");
		show(shorts.followers("wales", "12345"));
		System.out.println("Feed: wales | Expected result: NOT FOUND");
		show(shorts.getFeed( "wales", "12345" ));
		System.out.println("Likes: s4 | Expected result: NOT FOUND");
		show(shorts.likes(s4id, "12345"));
		System.out.println("Feed: liskov | Expected result: OK");
		show(shorts.getFeed( "liskov", "54321" ));

		//CosmosDBLayer.getInstance().clearAllContainers();
		//Cache.flushAll();
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