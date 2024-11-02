package main.test.last;

import tukano.api.Result;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.rest.TukanoRestServer;

import java.nio.ByteBuffer;
import java.util.Random;


public class Test {
	
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
		
		var serverURI = String.format("http://127.0.0.1:8080/rest/");
		
		var blobs = new RestBlobsClient( serverURI );
		var users = new RestUsersClient( serverURI);
		var shorts = new RestShortsClient(serverURI);

		/*
		* TEST USERS
		*
		 */

		/*System.out.println("***************TEST USERS************************");

		 show(users.createUser( new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales") ));
		 
		 show(users.createUser( new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov") ));
		 
		 show(users.updateUser("wales", "12345", new User("wales", "12345", "jimmy@wikipedia.com", "ola" ) ));

		 show(users.searchUsers(""));

		 show(users.deleteUser("wales", "12345"));
		 show(users.deleteUser("liskov", "54321"));//TODO:delete dá null*/

		/*
		* TEST SHORTS
		*
		 */
		System.out.println("***************TEST SHORTS 1 ************************");
		Result<Short> s1, s3;
		show(users.createUser( new User("wales1", "12345", "jimmy@wikipedia.pt", "Jimmy Wales") ));

		show(users.createUser( new User("liskov1", "54321", "liskov@mit.edu", "Barbara Liskov") ));
		show(users.createUser( new User("jb", "54321", "jb@mit.edu", "J B") ));


		show(s1 = shorts.createShort("liskov1", "54321"));
		show(shorts.createShort("liskov1", "54321"));
		show(shorts.createShort("liskov1", "54321"));
		show(s3 = shorts.createShort("wales1", "12345"));
		show(shorts.createShort("wales1", "12345"));

		show(shorts.getShort( s3.value().getShortId()));

		var shortId = s3.value().getShortId();
		System.out.println( "------->" + shortId );
		show(shorts.deleteShort(s3.value().getShortId(), "54321"));
		show(shorts.deleteShort(s3.value().getShortId(), "12345"));

		show(users.deleteUser("wales1", "12345"));//Check if all shorts are deleted

		show(shorts.createShort("jb", "54321"));
		show(shorts.getShorts("liskov1"));
		show(shorts.follow("liskov1", "jb", true, "54321"));
		show(shorts.followers("jb", "54321"));
		show(shorts.followers("liskov1", "54321"));//0 followers

		show(shorts.follow("jb", "liskov1", true, "54321"));
		show(shorts.followers("liskov1", "54321"));

		show(users.deleteUser("liskov1", "54321"));//Check if all shorts are deleted
		show(users.deleteUser("jb", "54321"));//Check if all shorts are deleted


		System.out.println("***************TEST SHORTS 2 ************************");
		Result<Short> s4, s5;
		show(users.createUser( new User("wales3", "123456", "jimmyn@wikipedia.pt", "Jimmy Walesn") ));
		show(users.createUser( new User("jb3", "54321", "email@jb.pt", "jbb")));
		show(s4 = shorts.createShort("wales3", "123456"));
		show(s5 = shorts.createShort("wales3", "123456"));
		var shortId4 = s4.value().getShortId();
		System.out.println( "------->" + shortId4 );
		show(shorts.like(shortId4, "jb3", true, "54321"));
		show(shorts.like(shortId4, "jb3", true, "54321"));//Conflict
		show(shorts.likes(shortId4 , "54321"));//Forbidden
		show(shorts.likes(shortId4 , "123456"));
		show(shorts.like(shortId4, "jb3", false, "54321"));
		show(shorts.likes(shortId4 , "123456"));

		var shortId5 = s5.value().getShortId();
		System.out.println( "------->" + shortId5 );
		show(shorts.like(shortId5, "jb3", true, "54321"));


		show(shorts.createShort("jb3", "54321"));
		show(shorts.getFeed("jb3", "54321")); //Deve aparecer so o do prorpio
		show(shorts.follow("jb3", "wales3", true, "54321"));
		show(shorts.getFeed("jb3", "54321")); //Shorts do proprio e wales3

		//show(shorts.deleteAllShorts("wales3", "123456", ));//Forbidden

		System.out.println("* Deletes *");
		show(users.deleteUser("wales3", "123456"));//Check if all shorts are deleted
		show(users.deleteUser("jb3", "54321"));//Check if all shorts are deleted


		/*var shortId = s1.value().getShortId();
		System.out.println( "------->" + shortId );
		var blobUrl = URI.create(s1.value().getBlobUrl());
		System.out.println( "------->" + blobUrl );
		
		var blobId = new File( blobUrl.getPath() ).getName();
		System.out.println( "BlobID:" + blobId );
		
		var token = blobUrl.getQuery().split("=")[1];

		blobs.upload(blobUrl.toString(), randomBytes(100), token);

		
		var s2id = s1.value().getShortId();
		
		show(shorts.follow("liskov", "wales", true, "54321"));
		show(shorts.followers("wales", "12345"));
		
		show(shorts.like(s2id, "liskov", true, "54321"));
		show(shorts.like(s2id, "liskov", true, "54321"));
		show(shorts.likes(s2id , "54321"));
		show(shorts.getFeed("liskov", "12345"));
		show(shorts.getShort( s2id ));
		
		show(shorts.getShorts( "wales" ));
		
		show(shorts.followers("wales", "12345"));

		show(shorts.getFeed("liskov", "12345"));

		show(shorts.getShort( s2id ));*/


		/*blobs( b -> {
			var r = b.download(blobId);
			System.out.println( Hex.of(Hash.sha256( bytes )) + "-->" + Hex.of(Hash.sha256( r.value() )));

		});*/


		System.exit(0);
	}
	
	
	private static Result<?> show( Result<?> res ) {
		if( res.isOK() )
			System.err.println("OK: " + res.value() );
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
