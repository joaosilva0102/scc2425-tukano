package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends tukano.api.Entity {

	@Id
	private String userId;
	private String pwd;
	private String email;
	private String displayName;

	public User() {}

	public User(String userId, String pwd, String email, String displayName) {
		super();
		this.id = userId;
		this.pwd = pwd;
		this.email = email;
		this.userId = userId;
		this.displayName = displayName;
	}

	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.id = userId;
		this.userId = userId;
	}
	public String getPwd() {
		return pwd;
	}
	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String id() { return id; }

	public String userId() {
		return userId;
	}

	public String pwd() {
		return pwd;
	}

	public String email() {
		return email;
	}

	public String displayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return "User [id=" + id + ", userId=" + userId + " pwd=" + pwd + ", email=" + email + ", displayName=" + displayName + "]";
	}

	public User copyWithoutPassword() {
		return new User(userId, "", email, displayName);
	}

	public User updateFrom( User other ) {
		return new User( userId,
				other.pwd != null ? other.pwd : pwd,
				other.email != null ? other.email : email,
				other.displayName != null ? other.displayName : displayName);
	}
}