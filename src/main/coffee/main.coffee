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
        
    @route "news/:uid/feeds/:fid", (uid, fid) ->
        this._uid = uid
        this._fid = fid
        
        # Specific feed listing.
        this.newsArticleView.show()
        this.articleList.reset()
        this.welcomeView.hide()
        
        # Get posts from server
        NR.API.GetPostsForFeed fid, 0, "", true, this._processFeedPosts, this._apiError
        
        # Update nav elements.
        index = this.feedList.any((i) -> i.id.toString() == fid.toString())
        feed = this.feedList.at index
        this.newsFeedView.feedSelected fid
        this.topNavView.feedSelected feed
        
    @route "news/:uid/feeds", (uid) ->
        this._uid = uid
        this._fid = 0
        
        # "All Feeds" listing.
        this.newsArticleView.show()
        this.articleList.reset()
        this.welcomeView.hide()
        
        # Get posts from server
        NR.API.GetAllPosts 0, "", true, this._processFeedPosts, this._apiError
     
        # Update nav elements.
        this.newsFeedView.allFeedsSelected()
        this.topNavView.allFeedsSelected()
        
    @route "news/:uid", (uid) ->
        this._uid = uid
        
        # Hide articles.
        this.newsArticleView.hide()
        this.articleList.reset()
        this.welcomeView.show()
        
        # Update nav elements.
        this.newsFeedView.homeSelected()
        this.topNavView.homeSelected()
        
    constructor: (bootstrappedFeeds) ->
        super()

        NR.API.Initialize()
        
        this.feedList = new SimpleMVC.Collection
        this.articleList = new SimpleMVC.Collection
        
        this.topNavView = new NR.Views.TopNavBar
        this.welcomeView = new NR.Views.WelcomeBlock
        this.newsArticleView = new NR.Views.NewsArticleListing this.articleList
        this.newsFeedView = new NR.Views.NewsFeedListing this.feedList
                
        for i in bootstrappedFeeds
            feed = new NR.Models.NewsFeedInfo
            for k,v of i
                feed[k] = v
            this.feedList.add feed
        
        # Set up timer for feed updates (every 5min).
        setInterval this.updateFeeds, 1000*60*5
    
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
            this.feedList.add feed
            this.selectFeed feed
        , this._apiError
    
    markAllRead: () =>
        if this._fid > 0
            NR.API.MarkAllFeedPostsAsRead this._fid, 0, Date.parse(this.articleList.at(0).article.pubDate) / 1000, (data) =>
                this.articleList.reset()
                this.updateFeeds()
                NR.API.GetPostsForFeed this._fid, 0, "", true, this._processFeedPosts, this._apiError
            , this._apiError
        else
            NR.API.MarkAllPostsAsRead 0, Date.parse(this.articleList.at(0).article.pubDate) / 1000, (data) =>
                this.articleList.reset()
                this.updateFeeds()
                NR.API.GetPostsForFeed this._fid, 0, "", true, this._processFeedPosts, this._apiError
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
