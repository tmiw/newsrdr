#############################################################################
# This file contains the newsrdr CoffeeScript/JavaScript API. See comment blocks
# for public accessible functions and properties. 
#
# Copyright 2013 Mooneer Salem. All rights reserved.
# See LICENSE file in the repository for license terms:
#    https://github.com/tmiw/newsrdr
#############################################################################

#############################################################################
# Note that all API calls are asynchronous and require callback functions.
# See AsyncResult definition below for more information.
#############################################################################

if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR
NR.API = {}

NR.API._initialized = false
NR.API._rootURL = "" #"http://newsrdr.us"

# Possible errors
NR.API.AuthenticationFailed = "auth_failed"
NR.API.ValidationFailed = "validation_failed"
NR.API.ServerError = "server_error"
NR.API.NotAFeedError = "not_a_feed"
NR.API.MultipleFeedsFoundError = "multiple_feeds_found"

NR.API.httpErrorCodeList = {
    401: NR.API.AuthenticationFailed
    422: NR.API.ValidationFailed
    500: NR.API.ServerError
}

NR.API.verifyInitialized = ->
    if (NR.API._initialized != true)
        throw new Error("NR.API.Initialize() must be called before using this function.")

class Version
    constructor: (major, minor, revision, patch) ->
        this.major = major
        this.minor = minor
        this.revision = revision
        this.patch = patch

#############################################################################
# Asynchronous result helper class. Performs the actual AJAX request
# and calls event handlers as appropriate.
#
# Callbacks are standard JavaScript functions:
#     * success: function(data) { ... }
#     * fail: function(error, detail) { ... }
#       (error can be any one of the constants in "Possible Constants" above.)
#############################################################################
class AsyncResult

    #############################################################################
    # Constructs and sends the request to the server.
    #############################################################################
    constructor: (method, url, data, success, fail) ->
        this._ajax = new XMLHttpRequest
        queryString = ""
        for k,v of data
            if Array.isArray(v)
                for i in v
                    queryString = queryString + encodeURIComponent(k.toString()) + "=" + encodeURIComponent(i.toString()) + "&"
            else
                queryString = queryString + encodeURIComponent(k.toString()) + "=" + encodeURIComponent(v.toString()) + "&"
        if url.indexOf("?") >= 0
            url = url + "&" + queryString
        else
            url = url + "?" + queryString
        
        this._ajax.addEventListener 'readystatechange', =>
            if this._ajax.readyState is 4 # complete
                try
                    successResultCodes = [200, 304]
                    if this._ajax.status in successResultCodes
                        try
                            jsonData = eval '(' + this._ajax.responseText + ')'
                        catch error
                            # Should never reach here.
                            jsonData = {
                                success: false
                                errorString: this._ajax.responseText
                            }
                            
                        
                        if jsonData.success
                            success.call(this, jsonData.data)
                        else
                            fail.call(this, NR.API.ServerError, jsonData.error_string, jsonData.data)
                    else
                        fail.call(this, NR.API.httpErrorCodeList[this._ajax.status], "", null)
                    
                    # Event triggered after request has completed.
                    if document?
                        event = new Event "NR.API.afterXHR"
                        document.dispatchEvent event
                catch error
                    if document?
                        event = new Event "NR.API.afterXHR"
                        document.dispatchEvent event
                    
                    throw error
        
        # Event triggered before request is sent.
        if document?
            event = new Event "NR.API.beforeXHR"
            document.dispatchEvent event
        
        this._ajax.open method, NR.API._rootURL + url, true
        this._ajax.send()
    
    #############################################################################
    # Aborts the request.
    #############################################################################
    abort: ->
        this._ajax.abort()

#############################################################################
# API version. Currently unused by server.  
#############################################################################      
NR.API.Version = new Version(1, 0, 0, "")

#############################################################################
# Initialize the API. Must be called before any other functions.
#############################################################################
NR.API.Initialize = ->
    # Set up XMLHttpRequest as appropriate. Mostly for older browsers.
    if (typeof @XMLHttpRequest == "undefined")
        @XMLHttpRequest = ->
            try
                return new ActiveXObject("Msxml2.XMLHTTP.6.0")
            catch error
                try
                  return new ActiveXObject("Msxml2.XMLHTTP.3.0")
                catch error
                    try
                        return new ActiveXObject("Microsoft.XMLHTTP")
                    catch error
                        throw new Error("This browser does not support XMLHttpRequest.")
    NR.API._initialized = true

#############################################################################
# Logs user into newsrdr. Note that this is only for people who've created
# their own accounts (vs. using Google+/Facebook/Twitter).
# Input: username and password + success/failure functions
# Output: none. Success function = username and password are correct, else
#         failure function is called.
#############################################################################
NR.API.Login = (username, password, successFn, failureFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "POST", "/auth/login/newsrdr", {username: username, password: password}, successFn, failureFn
    
#############################################################################
# Retrieves a list of feeds from the server.
# Input: success and failure functions
# Output: on success, a list of NewsFeedInfo objects:
#         {
#             'feed': {
#                 'id': ...,
#                 'title': ...,
#                 ...
#             },
#             'id': ...,
#             'numUnread': ...,
#             'errorsUpdating': ...
#         }
#############################################################################
NR.API.GetFeeds = (successFn, failFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "GET", "/feeds/", {}, successFn, failFn

#############################################################################
# Subscribes user to a new feed.
# Input: URL of the feed/website to add. If website, server will automatically 
#        search for RSS/Atom feeds within the HTML and add the first one found.
# Output: on success, a NewsFeedInfo object corresponding to the feed just added.
#############################################################################
NR.API.AddFeed = (feedUrl, successFn, failFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "POST", "/feeds/", {url: feedUrl}, successFn, failFn

#############################################################################
# Unsubscribes user from a feed.
# Input: feed ID (feed.id property in NewsFeedInfo object) or a NewsFeedInfo
#        object.
# Output: none.
#############################################################################
NR.API.RemoveFeed = (feedId, successFn, failFn) ->
    NR.API.verifyInitialized()
    feedId = feedId.id if feedId.id?
    feedId = feedId.feed.id if feedId.feed?
    new AsyncResult "DELETE", "/feeds/" + feedId, {}, successFn, failFn

#############################################################################
# Retrieves posts for the given feed.
# Input: 
#     * feedId: feed ID/NewsFeedInfo object (required)
#     * pageNumber: page number of posts to retrieve (default: first page)
#     * newestPostDate: the date of the newest post to show.
#     * newestPostId: the largest ID of the post to show.
#     * unreadOnly: whether to retrieve only unread posts (default: true)
# Output: on success, a list of NewsFeedArticleInfo objects:
#     {
#         'article': {
#             'id': ...,
#             'feedId': ...,
#             'title': ...,
#         },
#         'unread': ...,
#         'saved': ...
#     }
#############################################################################
NR.API.GetPostsForFeed = (feedId, pageNumber = 0, newestPostDate = "", newestPostId = "", unreadOnly = true, successFn, failFn) ->
    NR.API.verifyInitialized()
    feedId = feedId.id if feedId.id?
    feedId = feedId.feed.id if feedId.feed?
    new AsyncResult "GET", "/feeds/" + feedId + "/posts", {
        page: pageNumber,
        latest_post_id: newestPostId,
        latest_post_date: newestPostDate,
        unread_only: unreadOnly
    }, successFn, failFn

#############################################################################
# Retrieves posts for all feeds
# Input: 
#     * pageNumber: page number of posts to retrieve (default: first page)
#     * newestPostId: the largest post ID to show.
#     * newestPostDate: the date of the newest post to show.
#     * unreadOnly: whether to retrieve only unread posts (default: true)
# Output: on success, a list of NewsFeedArticleInfo objects:
#     {
#         'article': {
#             'id': ...,
#             'feedId': ...,
#             'title': ...,
#         },
#         'unread': ...,
#         'saved': ...
#     }
#############################################################################
NR.API.GetAllPosts = (pageNumber = 0, newestPostDate = "", newestPostId = "", unreadOnly = true, successFn, failFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "GET", "/posts", {
        page: pageNumber,
        latest_post_id: newestPostId,
        latest_post_date: newestPostDate,
        unread_only: unreadOnly
    }, successFn, failFn

#############################################################################
# Retrieves posts for multiple feeds
# Input: 
#     * feeds: array of feed IDs
#     * pageNumber: page number of posts to retrieve (default: first page)
#     * newestPostId: the largest post ID to show.
#     * newestPostDate: the date of the newest post to show.
#     * unreadOnly: whether to retrieve only unread posts (default: true)
# Output: on success, a list of NewsFeedArticleInfo objects:
#     {
#         'article': {
#             'id': ...,
#             'feedId': ...,
#             'title': ...,
#         },
#         'unread': ...,
#         'saved': ...
#     }
#############################################################################
NR.API.GetAllPostsInMultipleFeeds = (feeds, pageNumber = 0, newestPostDate = "", newestPostId = "", unreadOnly = true, successFn, failFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "GET", "/posts", {
        feeds: feeds,
        page: pageNumber,
        latest_post_id: newestPostId,
        latest_post_date: newestPostDate,
        unread_only: unreadOnly
    }, successFn, failFn
    
#############################################################################
# Retrieves saved posts for the given user.
# Input: 
#     * uid: the user's ID
#     * pageNumber: page number of posts to retrieve (default: first page)
#     * newestPostId: the largest post ID to show.
#     * newestPostDate: the date of the newest post to show.
# Output: on success, a list of NewsFeedArticleInfo objects:
#     {
#         'article': {
#             'id': ...,
#             'feedId': ...,
#             'title': ...,
#         },
#         'unread': ...,
#         'saved': ...
#     }
#############################################################################
NR.API.GetSavedPosts = (uid, pageNumber = 0, newestPostDate = "", newestPostId = "", successFn, failFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "GET", "/saved/" + uid + "/posts", {
        page: pageNumber,
        latest_post_id: newestPostId,
        latest_post_date: newestPostDate,
        unread_only: unreadOnly
    }, successFn, failFn
    
#############################################################################
# Marks post as read.
# Input: post ID (article.id property in NewsFeedArticleInfo object) or a 
#        NewsFeedArticleInfo object.
# Output: none.
#############################################################################
NR.API.MarkPostAsRead = (postId, successFn, failFn) ->
    NR.API.verifyInitialized()
    postId = postId.article.id if postId.article? && postId.article.id?
    new AsyncResult "DELETE", "/posts/" + postId, {}, successFn, failFn

#############################################################################
# Marks all posts in given feed as read.
# Input: 
#     * feed ID/NewsFeedInfo object (required)
#     * upTo: oldest date/time (seconds since 1 January 1970 00:00 UTC)
#             which to mark as read.
#     * from: newest date/time which to mark as read.
# Output: none.
#############################################################################
NR.API.MarkAllFeedPostsAsRead = (feedId, upTo = 0, from, successFn, failFn) ->
    NR.API.verifyInitialized()
    feedId = feedId.id if feedId.id?
    feedId = feedId.feed.id if feedId.feed?
    new AsyncResult "DELETE", "/feeds/" + feedId + "/posts", {
        upTo: upTo
        from: from
    }, successFn, failFn

#############################################################################
# Marks all posts in all feeds as read.
# Input: 
#     * upTo: oldest date/time (seconds since 1 January 1970 00:00 UTC)
#             which to mark as read.
#     * from: newest date/time which to mark as read.
# Output: none.
#############################################################################
NR.API.MarkAllPostsAsRead = (upTo = 0, from, successFn, failFn) ->
    NR.API.verifyInitialized()
    new AsyncResult "DELETE", "/posts", {
        upTo: upTo
        from: from
    }, successFn, failFn
    
#############################################################################
# Marks post as unread.
# Input: post ID (article.id property in NewsFeedArticleInfo object) or a 
#        NewsFeedArticleInfo object.
# Output: none.
#############################################################################
NR.API.MarkPostAsUnread = (postId, successFn, failFn) ->
    NR.API.verifyInitialized()
    postId = postId.article.id if postId.article? && postId.article.id?
    new AsyncResult "PUT", "/posts/" + postId, {}, successFn, failFn

#############################################################################
# Save post to "Saved Posts" page.
# Input: 
#     * feed ID
#     * post ID (article.id property in NewsFeedArticleInfo object) or a 
#       NewsFeedArticleInfo object.
# Output: none.
#############################################################################
NR.API.SavePost = (feedId, postId, successFn, failFn) ->
    NR.API.verifyInitialized()
    postId = postId.article.id if postId.article? && postId.article.id?
    new AsyncResult "PUT", "/posts/" + postId + "/saved", {feedId: feedId}, successFn, failFn

#############################################################################
# Unsave post from "Saved Posts" page.
# Input: 
#     * feed ID
#     * post ID (article.id property in NewsFeedArticleInfo object) or a 
#       NewsFeedArticleInfo object.
# Output: none.
#############################################################################
NR.API.UnsavePost = (feedId, postId, successFn, failFn) ->
    NR.API.verifyInitialized()
    postId = postId.article.id if postId.article? && postId.article.id?
    new AsyncResult "DELETE", "/posts/" + postId + "/saved", {feedId: feedId}, successFn, failFn

#############################################################################
# Whether to opt-out of sharing a user's feeds with unregistered users.
# This does not include any posts saved by the user.
# Input: optOut == true -> opts out of sharing.
# Output: none.
#############################################################################
NR.API.OptOutSharing = (optOut = false, successFn, failFn) ->
    NR.API.verifyInitialized()
    if optOut
        method = "POST"
    else
        method = "DELETE"
    new AsyncResult method, "/user/optout", {}, successFn, failFn