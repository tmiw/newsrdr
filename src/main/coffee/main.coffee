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
        errorText = "Communications error with the server. Please try again."
        
        switch type
            when NR.API.AuthenticationFailed then location.reload()
            when NR.API.ServerError then errorText = "The server encountered an error while processing the request. Please try again."
            
        noty({ text: errorText, layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" });
    
    @route "saved/:uid", (uid) ->
        this._uid = uid
        this._savedPostsMode = true
        this._postPage = 1
        this.newsArticleView.show()

    @route "news/:uid/feeds/add", (uid) ->
        this._uid = uid
        $("#addFeedUrl").val(this.urlParams["url"])
        $("#addFeed").modal()
        
    @route "news/:uid/feeds/:fid", (uid, fid) ->
        ga('send', 'pageview', {'title': document.title, 'page': location.pathname})
        index = this.feedList.any((i) -> i.id.toString() == fid.toString())
        if index >= 0
            this._uid = uid
            this._fid = fid
            this._postPage = 1
            this._enableFetch = true
            this.currentArticle = -1
            
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
        this.currentArticle = -1
        
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
        this._fid = null
        this.currentArticle = -1
        
        # Hide articles.
        this.newsArticleView.hide()
        this.articleList.reset()
        this.welcomeView.show()
        
        # Update nav elements.
        this.newsFeedView.homeSelected()
        this.topNavView.homeSelected()
        
    _adblocked: () ->
        $("#ad-body").html('<div class="sponsor_content"> 
           <center>
           <form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_blank"> 
           <span>newsrdr relies on your support.</span>
 <input type="hidden" name="cmd" value="_donations" /> 
 <input type="hidden" name="business" value="mooneer@gmail.com" /> 
 <input type="hidden" name="lc" value="US" /> 
 <input type="hidden" name="no_note" value="0" /> 
 <input type="hidden" name="currency_code" value="USD" /> 
 <input type="hidden" name="bn" value="PP-DonationsBF:btn_donateCC_LG.gif:NonHostedGuest" />
 <input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif" valign="center" style="display: inline;" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!" />
 <img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1" />
           </form>
           </center>
         </div>')
    

    navigate: (uri, executeFn) =>
        ret = super uri, executeFn
        #if ret
        $("#ad-body").html("<!-- newsrdr-new-site -->
<div id='div-gpt-ad-1379655552510-0' style='width:728px; height:90px;'>
<script type='text/javascript'>
googletag.cmd.push(function() {
    googletag.defineSlot('/70574502/newsrdr-new-site', [728, 90], 'div-gpt-ad-1379655552510-0').addService(googletag.pubads());
    googletag.pubads().enableSingleRequest();
    googletag.enableServices();
    });
googletag.cmd.push(function() { googletag.display('div-gpt-ad-1379655552510-0'); });
</script>
</div>")

        # Detect adblock and perform countermeasures
        setTimeout(() =>
            if ($("#ad-body div iframe").length == 0)
                this._adblocked();
        , 1000);
        ret
    
    _initializeKeyboardNavigation: ->
        # Set up keyboard navigation
        $(window).keypress (e) =>
            if (e.keyCode == 74 || e.keyCode == 75 || e.keyCode == 106 || e.keyCode == 107)
            
                # Scroll up/down one article.
                if this.articleList? && this.articleList.length > 0
                    if ((e.keyCode == 75 || e.keyCode == 107) && this.currentArticle?)
                        # Mark current article as read before proceeding.
                        article = this.articleList.at this.currentArticle
                        if this.authedUser && article? && article.unread
                            this.togglePostAsRead article
                        this.currentArticle = this.currentArticle - 1
                    else if ((e.keyCode == 74 || e.keyCode == 106) && this.currentArticle <= this.articleList.length - 1)
                        article = this.articleList.at this.currentArticle
                        if this.authedUser && article? && article.unread
                            # Mark current article as read before proceeding.
                            this.togglePostAsRead article
                        this.currentArticle = this.currentArticle + 1

                    if not this.currentArticle || this.currentArticle < 0
                        this.currentArticle = 0
                    else if this.currentArticle == this.articleList.length - 1
                        this.fetchMorePosts()   
                    else if this.currentArticle > this.articleList.length - 1
             
                        this.currentArticle = this.articleList.length - 1
                        
                    newArticle = this.articleList.at this.currentArticle
                    newArticleId = newArticle.article.id;
                    newArticleOffset = $("a[name='article" + newArticleId + "']").offset()
                    $('html, body').animate({
                        scrollTop: newArticleOffset.top - $("#top-nav-bar").height() - $("#ad-block").height() - $(".jumbotron").height()
                    }, 500)
                    e.preventDefault()
        
    constructor: (bootstrappedFeeds, bootstrappedPosts, optedOut, suppressLeftAndTop = false) ->
        super()

        NR.API.Initialize()
        
        this._initializeKeyboardNavigation()
        
        this.authedUser = not suppressLeftAndTop
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
    
    showAddFeedWindow: () =>
        this.navigate "/news/" + this._uid + "/feeds/add", true

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
                noty({ text: "Import complete.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "success" });
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
        noty({ text: "Feed import has begun.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "information" });
        window.setTimeout this.importSingleFeed, 0
    
    fetchMorePosts: =>
        errorWrapper = (type, desc) =>
            this._enableFetch = true
            this._apiError type, desc
        
        successWrapper = (data) =>
            if data.length > 0
                this._enableFetch = true
                this._postPage = this._postPage + 1
                this._processFeedPosts data
                
        if this._enableFetch
            this._enableFetch = false
            minId = this.articleList.at(0).article.id
            this.articleList.each (i) => 
                if i.article.id < minId
                    minId = i.article.id
                    
            if this._fid > 0
                NR.API.GetPostsForFeed(
                    this._fid, 
                    0, 
                    this.articleList.at(this.articleList.length - 1).article.id, 
                    this.localSettings.showOnlyUnread, 
                    successWrapper,
                    errorWrapper)
            else if this._savedPostsMode
                NR.API.GetSavedPosts(
                    this._uid,
                    0,
                    this.articleList.at(this.articleList.length - 1).article.id,
                    successWrapper,
                    errorWrapper)
            else
                NR.API.GetAllPosts(
                    0, 
                    this.articleList.at(this.articleList.length - 1).article.id, 
                    this.localSettings.showOnlyUnread,
                    successWrapper, 
                    errorWrapper)
    
    togglePostAsRead: (article) =>
        if article.unread
            NR.API.MarkPostAsRead article.article.id, (data) =>
                article.unread = false
                if article.feed.numUnread > 0
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

            noty({ text: errorText, layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" });
        else
            $('#importFeeds').modal('hide')
            
            # Queue up feeds for processing.
            this.localSettings.importQueue = result.feeds
            this.localSettings.feedsImported = 0
            this._beginFeedImport()

    toggleShowUnread: () =>
        this.localSettings.showOnlyUnread = !this.localSettings.showOnlyUnread
        this.articleList.reset()
        if this._fid?
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
