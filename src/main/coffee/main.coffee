if not @NR?
    NR = exports? and exports or @NR = {}
else
    NR = @NR

class NR.Application extends SimpleMVC.Controller
    _processFeedPosts: (data) =>
        for i in data
            post = new NR.Models.NewsFeedArticleInfo
            for k,v of i
                post[k] = v
            this.articleList.add post
    
    _apiError: (type, desc) =>
        # TODO
        alert "Error: " + type + " " + desc
    
    @route "saved/:uid", (uid) ->
        this._uid = uid
        this._savedPostsMode = true
        this._postPage = 1
        this.newsArticleView.show()
        
    @route "news/:uid/feeds/:fid", (uid, fid) ->
        ga('send', 'pageview', {'title': document.title, 'page': location.pathname})
        index = this.feedList.any((i) -> i.id.toString() == fid.toString())
        if index >= 0
            this._uid = uid
            this._fid = fid
            this._postPage = 1
            this._enableFetch = true
            
            # Specific feed listing.
            this.newsArticleView.show()
            this.articleList.reset()
            this.welcomeView.hide()
            
            # Get posts from server
            NR.API.GetPostsForFeed fid, 0, "", this.localSettings.showOnlyUnread, this._processFeedPosts, this._apiError
            
            # Update nav elements.        
            feed = this.feedList.at index
            this.newsFeedView.feedSelected fid
            this.topNavView.feedSelected feed
            true
        else
            false
            
    @route "news/:uid/feeds", (uid) ->
        ga('send', 'pageview', {'title': document.title, 'page': location.pathname})
        this._uid = uid
        this._fid = 0
        this._postPage = 1
        this._enableFetch = true
        
        # "All Feeds" listing.
        this.newsArticleView.show()
        this.articleList.reset()
        this.welcomeView.hide()
        
        # Get posts from server
        NR.API.GetAllPosts 0, "", this.localSettings.showOnlyUnread, this._processFeedPosts, this._apiError
     
        # Update nav elements.
        this.newsFeedView.allFeedsSelected()
        this.topNavView.allFeedsSelected()
        
    @route "news/:uid", (uid) ->
        ga('send', 'pageview', {'title': document.title, 'page': location.pathname})
        this._uid = uid
        
        # Hide articles.
        this.newsArticleView.hide()
        this.articleList.reset()
        this.welcomeView.show()
        
        # Update nav elements.
        this.newsFeedView.homeSelected()
        this.topNavView.homeSelected()
        
    constructor: (bootstrappedFeeds, bootstrappedPosts, optedOut, suppressLeftAndTop = false) ->
        super()

        NR.API.Initialize()
        
        this.feedList = new SimpleMVC.Collection
        this.articleList = new SimpleMVC.Collection
        this.localSettings = new NR.Models.HtmlLocalStorage
        this.localSettings.optedOut = optedOut
        
        if not suppressLeftAndTop
            this.topNavView = new NR.Views.TopNavBar
            this.topNavView.model = this.localSettings
            this.welcomeView = new NR.Views.WelcomeBlock
            this.newsFeedView = new NR.Views.NewsFeedListing this.feedList
            
            for i in bootstrappedFeeds
                feed = new NR.Models.NewsFeedInfo
                for k,v of i
                    feed[k] = v
                this.feedList.add feed
                
            # Set up timer for feed updates (every 5min).
            setInterval this.updateFeeds, 1000*60*5
        
            # Restart feed import as needed
            if this.localSettings.importQueue? && this.localSettings.importQueue.length > 0
                this._beginFeedImport()
                
        this.newsArticleView = new NR.Views.NewsArticleListing this.articleList
        for i in bootstrappedPosts
            post = new NR.Models.NewsFeedArticleInfo
            for k,v of i
                post[k] = v
            this.articleList.add post
        
    selectAllFeeds: () =>
        this.navigate "/news/" + this._uid + "/feeds", true
        
    selectFeed: (feed) =>
        this.navigate "/news/" + this._uid + "/feeds/" + feed.id, true
    
    deselectFeed: () =>
        this.navigate "/news/" + this._uid, true
    
    removeCurrentFeed: () =>
        currentFeed = this._fid
        NR.API.RemoveFeed this._fid, () =>
            index = this.feedList.any((i) -> i.id.toString() == currentFeed.toString())
            this.feedList.removeAt index
            this.navigate "/news/" + this._uid + "/feeds", true
            
    addFeed: (url) =>
        NR.API.AddFeed url, (data) =>
            feed = new NR.Models.NewsFeedInfo
            for k,v of data
                feed[k] = v
            if this.feedList.any((i) -> i.id == feed.id) == -1
                this.feedList.add feed
            this.selectFeed feed
        , this._apiError
    
    markAllRead: () =>
        if this._fid > 0
            NR.API.MarkAllFeedPostsAsRead this._fid, 0, Date.parse(this.articleList.at(0).article.pubDate) / 1000, (data) =>
                this.articleList.reset()
                this.updateFeeds()
                NR.API.GetPostsForFeed this._fid, 0, "", this.localSettings.showOnlyUnread, this._processFeedPosts, this._apiError
            , this._apiError
        else
            NR.API.MarkAllPostsAsRead 0, Date.parse(this.articleList.at(0).article.pubDate) / 1000, (data) =>
                this.articleList.reset()
                this.updateFeeds()
                NR.API.GetAllPosts 0, "", this.localSettings.showOnlyUnread, this._processFeedPosts, this._apiError
            , this._apiError
    
    importSingleFeed: () =>
        nextImport = () =>
            this.localSettings.importQueue.shift()
            this.localSettings.feedsImported = this.localSettings.feedsImported + 1
            
            if this.localSettings.importQueue.length > 0
                # import next feed
                window.setTimeout this.importSingleFeed, 0
            else
                # all done
                # TODO: alert user that it's complete.
                this.localSettings.importQueue = []
                
        # Unlike addFeed above, we're ignoring errors from the server.
        NR.API.AddFeed this.localSettings.importQueue[0], (data) =>
            feed = new NR.Models.NewsFeedInfo
            for k,v of data
                feed[k] = v
            if this.feedList.any((i) -> i.id == feed.id) == -1
                this.feedList.add feed         
            nextImport.call(this)
        , nextImport.bind(this)
        
    _beginFeedImport: () =>
        # TODO: let user know import's begun
        window.setTimeout this.importSingleFeed, 0
    
    fetchMorePosts: =>
        if this._enableFetch
            if this._fid > 0
                NR.API.GetPostsForFeed this._fid, this._postPage, Date.parse(this.articleList.at(0).article.pubDate) / 1000, this.localSettings.showOnlyUnread, (data) =>
                    if data.length == 0
                        this._enableFetch = false
                    else
                        this._postPage = this._postPage + 1
                        this._processFeedPosts data
                , this._apiError
            else if this._savedPostsMode
                NR.API.GetSavedPosts this._uid, this._postPage, Date.parse(this.articleList.at(0).article.pubDate) / 1000, (data) =>
                    if data.length == 0
                        this._enableFetch = false
                    else
                        this._postPage = this._postPage + 1
                        this._processFeedPosts data
                , this._apiError
            else
                NR.API.GetAllPosts this._postPage, Date.parse(this.articleList.at(0).article.pubDate) / 1000, this.localSettings.showOnlyUnread, (data) =>
                    if data.length == 0
                        this._enableFetch = false
                    else
                        this._postPage = this._postPage + 1
                        this._processFeedPosts data
                , this._apiError
    
    togglePostAsRead: (article) =>
        if article.unread
            NR.API.MarkPostAsRead article.article.id, (data) =>
                article.unread = false
                article.feed.numUnread = article.feed.numUnread - 1
            , this._apiError
        else
            NR.API.MarkPostAsUnread article.article.id, (data) =>
                article.unread = true
                article.feed.numUnread = article.feed.numUnread + 1
            , this._apiError
    
    toggleSavePost: (article) =>
        if article.saved
            NR.API.UnsavePost article.feed.id, article.article.id, (data) =>
                article.saved = false
            , this._apiError
        else
            NR.API.SavePost article.feed.id, article.article.id, (data) =>
                article.saved = true
            , this._apiError
            
    finishedUploadingFeedList: (result) =>
        if (!result.success)
            errorText = "Error encountered while uploading file."
            if (result.reason == "forgot_file")
                errorText = "Please select a file and try again."
            else if (result.reason == "cant_parse")
                errorText = "The file provided is not a valid OPML file. Select another file and try again."
            else if (result.reason == "not_authorized")
                # Force user to login screen.
                location.reload();
            else if (result.reason == "too_big")
                errorText = "The file provided is too big to be parsed. Select another file and try again."

            # TODO: display alert
        else
            $('#importFeeds').modal('hide')
            
            # Queue up feeds for processing.
            this.localSettings.importQueue = result.feeds
            this.localSettings.feedsImported = 0
            this._beginFeedImport()

    toggleShowUnread: () =>
        this.localSettings.showOnlyUnread = !this.localSettings.showOnlyUnread
        this.articleList.reset()
        if this._fid > 0
            NR.API.GetPostsForFeed this._fid, 0, "", this.localSettings.showOnlyUnread, this._processFeedPosts, this._apiError
            index = this.feedList.any((i) => i.id.toString() == this._fid.toString())
            feed = this.feedList.at index
            this.topNavView.feedSelected feed
        else
            NR.API.GetAllPosts 0, "", this.localSettings.showOnlyUnread, this._processFeedPosts, this._apiError
            this.topNavView.allFeedsSelected

    toggleOptOut: =>
        NR.API.OptOutSharing (not this.localSettings.optedOut), () =>
            this.localSettings.optedOut = not this.localSettings.optedOut
        , this._apiError

    updateFeeds: =>
        NR.API.GetFeeds (feeds) =>
            for i in feeds
                feed = new NR.Models.NewsFeedInfo
                for k,v of i
                    feed[k] = v
                
                index = this.feedList.any (x) -> feed.id == x.id
                if index >= 0
                    # Don't use Collection#replace so we can maintain active state.
                    other = this.feedList.at index
                    other.feed = feed.feed
                    other.id = feed.id
                    other.numUnread = feed.numUnread
                    other.errorsUpdating = feed.errorsUpdating
                else
                    this.feedList.add feed
            
            # Remove feeds that no longer exist.
            # TODO
        , () ->
