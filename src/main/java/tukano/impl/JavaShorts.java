package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import utils.PostgreSQL.CosmosPostgresDB;

public class JavaShorts implements Shorts {

    private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
    private static CosmosPostgresDB<Object> postgre = CosmosPostgresDB.getInstance();
    private static Shorts instance;
    private boolean nosql = false;

    synchronized public static Shorts getInstance() {
        if (instance == null)
            instance = new JavaShorts();
        return instance;
    }

    private JavaShorts() {
    }


    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult(okUser(userId, password), user -> {

            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
            var shrt = new Short(shortId, userId, blobUrl);

            if (nosql) {
                return errorOrValue(
                        DB.insertOne(shrt), s -> {
                            Cache.insertIntoCache("short", shortId, s);
                            //JavaBlobs.getInstance().upload(blobUrl,  , Token.get(shortId));
                            return s.copyWithLikes_And_Token(0);
                        });
            } else {
                return errorOrValue(
                        postgre.insertOne(shrt), s -> {
                            Cache.insertIntoCache("short", shortId, s);
                            return s.copyWithLikes_And_Token(0);
                        });
            }
        });
    }

    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if (shortId == null)
            return error(BAD_REQUEST);

        Result<Short> result = Cache.getFromCache("short", shortId, Short.class);
        // TODO Update cache
        // TODO Cache likes (?) Maybe create Az Function to update likes
        var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);

        var likes = DB.sql(query, Likes.class).size();

        if (!result.isOK()) {
            if (nosql)
                result = DB.getOne(shortId, Short.class);
            else
                result = postgre.getOne(shortId, Short.class);

        }
//            Log.log(Level.WARNING, "AQUII: " + cacheRes);
//            return errorOrValue(cacheRes.isOK() ? cacheRes : nosql ? DB.getOne(shortId, Short.class) : CosmosPostgresDB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token(likes));
        return result;
    }

    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {

            return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
                Cache.removeFromCache("short", shortId);
                if (nosql) {
                    var res = DB.deleteOne(shrt);
                } else {
                    postgre.deleteOne(shrt);
                }

                var query = format("SELECT l FROM Likes l WHERE l.shortId = '%s'", shortId);
                List<Likes> itemsToDelete;

                if (nosql) {
                    itemsToDelete = DB.sql(query, Likes.class);
                } else {
                    try {
                        itemsToDelete = postgre.query(Likes.class, query);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                for (Likes like : itemsToDelete) {
                    if (nosql) {
                        DB.deleteOne(like);
                    } else {

                        postgre.deleteOne(like);

                    }
                }

                /*var res = JavaBlobs.getInstance().delete(shrt.getShortId(), Token.get(shrt.getShortId()));
                if (!res.isOK()) {
                    return res;
                }*/

                return Result.ok();
            });
        });
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
        if (nosql)
            return errorOrValue(okUser(userId), DB.sql(query, Short.class)
                    .stream().map(Short::getShortId).collect(Collectors.toList()));
        else {
            try {
                return errorOrValue(okUser(userId), postgre.query(Short.class, query)
                        .stream().map(Short::getShortId).collect(Collectors.toList()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            if (nosql)
                return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
            else {
                return errorOrVoid(okUser(userId2), isFollowing ? postgre.insertOne(f) : postgre.deleteOne(f));
            }
        });
    }

    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
        if (nosql)
            return errorOrValue(okUser(userId, password), DB.sql(query, Following.class)
                    .stream().map(Following::getFollower).collect(Collectors.toList()));
        else {
            try {
                return errorOrValue(okUser(userId, password), postgre.query(Following.class, query)
                        .stream().map(Following::getFollower).collect(Collectors.toList()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
                password));

        if (nosql) {
            return errorOrResult(getShort(shortId), shrt -> {
                var l = new Likes(userId, shortId, shrt.getOwnerId());
                return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
            });
        } else {
            return errorOrResult(getShort(shortId), shrt -> {
                var l = new Likes(userId, shortId, shrt.getOwnerId());
                return errorOrVoid(okUser(userId, password), isLiked ? postgre.insertOne(l) : postgre.deleteOne(l));
            });

        }

    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {

            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

            if (nosql)
                return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, Likes.class)
                        .stream().map(Likes::getUserId).collect(Collectors.toList()));
            else {
                try {
                    return errorOrValue(okUser(shrt.getOwnerId(), password), postgre.query(Likes.class, query)
                            .stream().map(Likes::getUserId).collect(Collectors.toList()));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

        final var QUERY_1_FMT = """
                	SELECT * FROM Following f
                	WHERE f.follower = '%s'
                """;
        Result<List<String>> result;
        if (nosql)
            result = errorOrValue(okUser(userId, password), DB.sql(format(QUERY_1_FMT, userId), Following.class)
                    .stream().map(Following::getFollowee).collect(Collectors.toList()));
        else {
            try {
                result = errorOrValue(okUser(userId, password), postgre.query(Following.class, format(QUERY_1_FMT, userId))
                        .stream().map(Following::getFollowee).collect(Collectors.toList()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        }

        if (!result.isOK()) return result;

        var usersList = result.value();

        usersList.add(userId);

        String usersFormated = usersList.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));

        final var QUERY_2_FMT = """
                	SELECT * FROM Short s
                	WHERE s.ownerId IN (%s)
                	ORDER BY s.timestamp DESC
                """;

        if (nosql) {
            return errorOrValue(okUser(userId, password), DB.sql(format(QUERY_2_FMT, usersFormated), Short.class)
                    //.stream().map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList()));
                    .stream().map(Short -> Short.getShortId()).collect(Collectors.toList()));
        } else {
            try {
                return errorOrValue(okUser(userId, password), postgre.query(Short.class, format(QUERY_2_FMT, usersFormated))
                        .stream().map(Short -> Short.getShortId()).collect(Collectors.toList()));
                //.stream().map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected Result<User> okUser(String userId, String pwd) {
        return JavaUsers.getInstance().getUser(userId, pwd);
    }

    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return ok();
        else
            return error(res.error());
    }

    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

        if (!Token.isValid(token, userId))
            return error(FORBIDDEN);

        // delete shorts
        var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
        List<Short> res1;
        try {
            if (nosql) {
                res1 = DB.sql(query1, Short.class);
            } else {
                res1 = postgre.query(Short.class, query1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        for (Short s : res1) {
            boolean deleteSuccess;
            if (nosql) {
                deleteSuccess = DB.deleteOne(s).isOK();
            } else {
                deleteSuccess = postgre.deleteOne(s).isOK();
            }
            if (!deleteSuccess)
                return error(BAD_REQUEST);
        }

        // delete follows
        var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
        List<Following> res2;
        try {
            if (nosql) {
                res2 = DB.sql(query2, Following.class);
            } else {
                res2 = postgre.query(Following.class, query2);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        for (Following f : res2) {
            boolean deleteSuccess;
            if (nosql) {
                deleteSuccess = DB.deleteOne(f).isOK();
            } else {
                deleteSuccess = postgre.deleteOne(f).isOK();
            }
            if (!deleteSuccess)
                return error(BAD_REQUEST);
        }

        // delete likes
        var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
        List<Likes> res3;
        try {
            if (nosql) {
                res3 = DB.sql(query3, Likes.class);
            } else {
                res3 = postgre.query(Likes.class, query3);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        for (Likes l : res3) {
            boolean deleteSuccess;
            if (nosql) {
                deleteSuccess = DB.deleteOne(l).isOK();
            } else {
                deleteSuccess = postgre.deleteOne(l).isOK();
            }
            if (!deleteSuccess)
                return error(BAD_REQUEST);
        }
        return Result.ok();
    }

}