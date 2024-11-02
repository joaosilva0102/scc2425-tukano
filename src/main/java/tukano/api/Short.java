package tukano.api;

import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tukano.impl.Token;

/**
 * Represents a Short video uploaded by an user.
 *
 * A short has an unique shortId and is owned by a given user;
 * Comprises of a short video, stored as a binary blob at some bloburl;.
 * A post also has a number of likes, which can increase or decrease over time. It is the only piece of information that is mutable.
 * A short is timestamped when it is created.
 *
 */
@Entity
public class Short {
	@Id
	String shortId;
	String id;
	String ownerId;
	String blobUrl;
	long timestamp;
	int totalLikes;
	int totalviews;

	public Short() {}

	public Short(String shortId, String ownerId, String blobUrl, long timestamp, int totalLikes) {
		super();
		this.shortId = shortId;
		this.id = shortId;
		this.ownerId = ownerId;
		this.blobUrl = blobUrl;
		this.timestamp = timestamp;
		this.totalLikes = totalLikes;
		this.totalviews = 0;
	}

	public Short(String shortId, String ownerId, String blobUrl) {
		this( shortId, ownerId, blobUrl, System.currentTimeMillis(), 0);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getShortId() {
		return id;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
		this.id = shortId;
	}

	public String getOwnerId() { return ownerId; }

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getBlobUrl() {
		return blobUrl;
	}

	public void setBlobUrl(String blobUrl) {
		this.blobUrl = blobUrl;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public int getTotalLikes() {
		return totalLikes;
	}

	public void setTotalLikes(int totalLikes) {
		this.totalLikes = totalLikes;
	}

	public int getTotalViews() {
		return totalviews;
	}
	public void setTotalviews(int totalviews) {
		this.totalviews = totalviews;
	}

	public int incrementViews() {
		return totalviews++;
	}

	@Override
	public String toString() {
		return "Short [shortId=" + id + ", ownerId=" + ownerId + ", blobUrl=" + blobUrl + ", timestamp="
				+ timestamp + ", totalLikes=" + totalLikes + ", totalviews=" + totalviews + "]";
	}

	public Short copyWithLikes_And_Token( int totLikes) {
		var urlWithToken = String.format("%s?token=%s", blobUrl, Token.get(id, timestamp));
		return new Short( id, ownerId, urlWithToken, timestamp, totLikes);
	}
}