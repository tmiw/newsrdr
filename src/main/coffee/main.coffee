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
        
        # Specific feed listing.
        this.newsArticleView.show()
        this.articleList.reset()
        this.welcomeView.hide()
        
        # Get posts from server
        NR.API.GetPostsForFeed fid, 0, "", true, this._processFeedPosts, this._apiError
        
        # Update left hand side.
        this.newsFeedView.feedSelected fid
        
    @route "news/:uid/feeds", (uid) ->
        this._uid = uid
    
        # "All Feeds" listing.
        this.newsArticleView.show()
        this.articleList.reset()
        this.welcomeView.hide()
        
        # Get posts from server
        NR.API.GetAllPosts 0, "", true, this._processFeedPosts, this._apiError
     
        # Update left hand side.
        this.newsFeedView.allFeedsSelected()
        
    @route "news/:uid", (uid) ->
        this._uid = uid
        
        # Hide articles.
        this.newsArticleView.hide()
        this.articleList.reset()
        this.welcomeView.show()
        
        # Update left hand side.
        this.newsFeedView.homeSelected()
        
    constructor: (bootstrappedFeeds) ->
        super()

        NR.API.Initialize()
        
        this.feedList = new SimpleMVC.Collection
        this.articleList = new SimpleMVC.Collection
        
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
        
    addFeed: (url) =>
        NR.API.AddFeed url, (data) =>
            feed = new NR.Models.NewsFeedInfo
            for k,v of data
                feed[k] = v
            this.feedList.add feed
            this.selectFeed feed
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
