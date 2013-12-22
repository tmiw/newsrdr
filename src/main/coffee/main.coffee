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
            #if (!this.localSettings.showOnlyUnread || (this.localSettings.showOnlyUnread && post.unread))
            #    if post.unread
            #        this._seenUnread = this._seenUnread + 1
            #    this.articleList.add post
            this.articleList.add post
            
    _apiError: (type, desc, data) =>
        errorText = "Communications error with the server. Please try again."
        
        switch type
            when NR.API.AuthenticationFailed then location.reload()
            when NR.API.ServerError
                if desc == NR.API.NotAFeedError
                    this._createFeedView = new NR.Views.CreateFeedWindow
                    feedModel = new NR.Models.CreateFeedModel
                    feedModel.baseHtml = data
                    feedModel.baseUrl = $("#addFeedUrl").val()
                    this._createFeedView.model = feedModel
                    this._createFeedView.show()
                else if desc == NR.API.MultipleFeedsFoundError
                    foundList = new SimpleMVC.Collection
                    this._multipleFeedFoundView = new NR.Views.MultipleFeedsFoundWindow foundList
                    this._multipleFeedFoundView.show()
                    for i in data
                        entry = new NR.Models.MultipleFeedEntry
                        entry.title = i.title
                        entry.url = i.url
                        foundList.add entry
                else
                    errorText = "The server encountered an error while processing the request. Please try again."
            
        if desc != NR.API.NotAFeedError && desc != NR.API.MultipleFeedsFoundError
            noty({ text: errorText, layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" });
    
    @route "saved/:uid", (uid) ->
        this._uid = uid
        this._savedPostsMode = true
        this._postPage = 1
        this._maxId = ""
        this.newsArticleView.show()

    @route "news/:uid/feeds/add", (uid) ->
        this._uid = uid
        $("#addFeedUrl").val(this.urlParams["url"])
        $("#addFeed").on("shown.bs.modal", () -> $("#addFeedUrl").focus())
        $("#addFeed").on("hidden.bs.modal", () => 
            # return to original page
            if this._fid
                this.navigate("/news/" + this._uid + "/feeds/" + this._fid)
            else if this._fid == 0
                this.navigate("/news/" + this._uid + "/feeds")
            else
                this.navigate("/news/" + this._uid))
        
        $("#addFeed").modal()
        
    @route "news/:uid/feeds/:fid", (uid, fid) ->
        index = this.feedList.any((i) -> i.id.toString() == fid.toString())
        if index >= 0
            this._uid = uid
            this._fid = fid
            this._maxId = ""
            this._postPage = 0
            this._seenUnread = 0
            this._enableFetch = true
            this.currentArticle = -1
            
            # Specific feed listing.
            this.newsArticleView.show()
            this.articleList.reset()
            this.welcomeView.hide()
            
            # Get posts from server
            this.fetchMorePosts()
            
            # Update nav elements.        
            feed = this.feedList.at index
            this.newsFeedView.feedSelected fid
            this.topNavView.feedSelected feed
            true
        else
            false
            
    @route "news/:uid/feeds", (uid) ->
        this._uid = uid
        this._fid = 0
        this._postPage = 0
        this._seenUnread = 0
        this._maxId = ""
        this._enableFetch = true
        this.currentArticle = -1
        
        # "All Feeds" listing.
        this.newsArticleView.show()
        this.articleList.reset()
        this.welcomeView.hide()
        
        # Get posts from server
        this.fetchMorePosts()
     
        # Update nav elements.
        this.newsFeedView.allFeedsSelected()
        this.topNavView.allFeedsSelected()
        
    @route "news/:uid", (uid) ->
        this._uid = uid
        this._fid = null
        this._maxId = ""
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
    
    submitUpdateProfileBox: () =>
        good = false
        
        if event?
            event.preventDefault()
        
        if $('#profileEmail').val().length == 0
            noty({ text: "Email is required.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" })
        else if !$('#profileEmail').val().match(/^[-0-9A-Za-z!#$%&'*+/=?^_`{|}~.]+@[-0-9A-Za-z!#$%&'*+/=?^_`{|}~.]+/)
            noty({ text: "Email address is invalid.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" })
        else
            if $('#profilePassword').val().length > 0
                if $('#profilePassword').val().length < 8
                    noty({ text: "Password must be at least eight characters long.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" })
                else if $('#profilePassword').val() != $('#profilePassword2').val()
                    noty({ text: "Both passwords must match in order to reset your password.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "error" })
                else
                    good = true
            else
                good = true
        
        if good == true
            successWrapper = (data) =>
                noty({ text: "Profile updated.", layout: "topRight", timeout: 2000, dismissQueue: true, type: "success" })
                this.profileModel.email = $('#profileEmail').val()
            
            NR.API.UpdateProfile(
                $('#profileEmail').val(), 
                $('#profilePassword').val(),
                $('#profilePassword2').val(), 
                successWrapper, 
                this._apiError)
            
            $("#updateProfile").modal('hide')
    
    submitAddFeedBox: =>
        if event?
            event.preventDefault()
        if $('#addFeedUrl').val().length > 0
            this.addFeed($('#addFeedUrl').val())
            $('#addFeed').modal('hide')

    navigate: (uri, executeFn = false, addState = true) =>
        ret = super uri, executeFn, addState
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
    
    _modalVisible: ->
        $(".modal[aria-hidden!='true']").length > 0
    
    _handlePostKeys: (e) =>
        kc = e.keyCode || e.charCode
        # Scroll up/down one article.
        if this.articleList? && this.articleList.length > 0
            if ((kc == 75 || kc == 107) && this.currentArticle?)
                # Mark current article as read before proceeding.
                article = this.articleList.at this.currentArticle
                if this.authedUser && article? && article.unread
                    this.togglePostAsRead article
                this.currentArticle = this.currentArticle - 1
            else if ((kc == 74 || kc == 106) && this.currentArticle <= this.articleList.length - 1)
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
    
    _handleFeedKeys: (e) =>
        kc = e.keyCode || e.charCode
        if this.newsFeedView.canNavigateDownList() && (kc == 115 || kc == 83)
            # s/S (navigate down list)
            initUrl = "/news/" + this._uid + "/feeds"
            nextFeed = this.newsFeedView.getNextFeed()
            if nextFeed > 0
                initUrl = initUrl + "/" + nextFeed
            this.navigate initUrl, true
            
        else if this.newsFeedView.canNavigateUpList() && (kc == 119 || kc == 87)
            # w/W (navigate up list)
            initUrl = "/news/" + this._uid
            nextFeed = this.newsFeedView.getPrevFeed()
            if nextFeed? && nextFeed >= 0
                initUrl = initUrl + "/feeds"
                if nextFeed > 0
                    initUrl = initUrl + "/" + nextFeed
            this.navigate initUrl, true
            
        e.preventDefault()
    
    _handleOpenArticleKeys: (e) =>
        if this.articleList? && this.articleList.length > 0
           if !this.currentArticle? || this.currentArticle < 0 || this.currentArticle >= this.articleList.length
               this.currentArticle = 0
            
           article = this.articleList.at this.currentArticle
           articleId = article.article.id;
           articleOffset = $("a[name='article" + articleId + "']").offset()
           $('html, body').animate({
               scrollTop: articleOffset.top - $("#top-nav-bar").height() - $("#ad-block").height() - $(".jumbotron").height()
           }, 500)
           e.preventDefault()
            
           window.open article.article.link, "_blank"
           if this.authedUser && article? && article.unread
               this.togglePostAsRead article
                
    _initializeKeyboardNavigation: ->
        # Set up keyboard navigation
        $(window).keypress (e) =>
            if not this._modalVisible()
                kc = e.keyCode || e.charCode
                if (kc == 74 || kc == 75 || kc == 106 || kc == 107)
                    # j/k/J/K (post navigation)
                    this._handlePostKeys e
                else if (kc == 63)
                    # ? (help)
                    e.preventDefault()
                    $("#keyboardHelp").modal("show")
                else if (kc == 119 || kc == 87 || kc == 115 || kc == 83)
                    # w/W/s/S (feed navigation)
                    this._handleFeedKeys e
                else if (kc == 114 || kc == 82)
                    # r/R (mark all as read)
                    e.preventDefault()
                    this.markAllRead()
                else if (kc == 100 || kc == 68)
                    # d/D (deletes current feed)
                    e.preventDefault()
                    if not $("#removeFeedLink").parent().hasClass("disabled")
                        $("#removeFeedConfirm").modal()
                else if (kc == 97 || kc == 65)
                    # a/A (add new feed)
                    e.preventDefault()
                    this.showAddFeedWindow()
                else if (kc == 79 || kc == 111)
                    # o/O (open current article in new window)
                    this._handleOpenArticleKeys e
                    
    constructor: (bootstrappedFeeds, bootstrappedPosts, optedOut, suppressLeftAndTop, email = "") ->
        super()

        NR.API.Initialize()
        
        this._initializeKeyboardNavigation()
        
        this.authedUser = not suppressLeftAndTop
        this.feedList = new SimpleMVC.Collection
        this.articleList = new SimpleMVC.Collection
        this.localSettings = new NR.Models.HtmlLocalStorage
        this.localSettings.optedOut = optedOut
        
        if not suppressLeftAndTop
            this.profileModel = new NR.Models.ProfileModel
            this.profileModel.email = email
              
            this.topNavView = new NR.Views.TopNavBar
            this.topNavView.model = this.localSettings
            this.welcomeView = new NR.Views.WelcomeBlock
            this.newsFeedView = new NR.Views.NewsFeedListing this.feedList
        
            if email != ""
                $("#updateProfileLink").parent().removeClass "disabled"
                
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
        successFn = (data) =>
            this.articleList.reset()
            this.updateFeeds()
            this._enableFetch = true
            this._maxId = ""
            this._postPage = 0
            this._seenUnread = 0
            this.fetchMorePosts()
                
        if this._fid > 0
            NR.API.MarkAllFeedPostsAsRead this._fid, 0, Date.parse(this.articleList.at(0).article.pubDate) / 1000, successFn, this._apiError
        else
            NR.API.MarkAllPostsAsRead 0, Date.parse(this.articleList.at(0).article.pubDate) / 1000, successFn, this._apiError
    
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
            if data.list.length > 0
                this._enableFetch = true
                this._maxId = data.id
                this._postPage = this._postPage + 1
                this._processFeedPosts data.list
                #if (this._seenUnread - this._lastSeenUnread) < 10 && this.localSettings.showOnlyUnread
                #    if this.newsFeedView? && this._seenUnread < this.newsFeedView.getSelectedUnread()
                #        this.fetchMorePosts()
                #    else
                #        this._enableFetch = false
                #        this._lastSeenUnread = null
                #else
                #    this._lastSeenUnread = null
        
        if not this._lastSeenUnread?
            this._lastSeenUnread = this._seenUnread
            
        if this._postPage > 0
            lastArticleId = Date.parse(this.articleList.at(this.articleList.length - 1).article.pubDate) / 1000
        else
            lastArticleId = ""
            
        if this._enableFetch
            this._enableFetch = false
            if this._fid > 0
                NR.API.GetPostsForFeed(
                    this._fid, 
                    0, 
                    lastArticleId, 
                    this._maxId,
                    this.localSettings.showOnlyUnread, 
                    successWrapper,
                    errorWrapper)
            else if this._savedPostsMode
                NR.API.GetSavedPosts(
                    this._uid,
                    0,
                    lastArticleId,
                    this._maxId,
                    successWrapper,
                    errorWrapper)
            else
                NR.API.GetAllPosts(
                    0, 
                    lastArticleId, 
                    this._maxId,
                    this.localSettings.showOnlyUnread,
                    successWrapper, 
                    errorWrapper)
    
    togglePostAsRead: (article) =>
        if article.unread
            NR.API.MarkPostAsRead article.article.id, (data) =>
                article.unread = false
                if article.feed.numUnread > 0
                    article.feed.numUnread = article.feed.numUnread - 1
                this.newsFeedView.sort()
            , this._apiError
        else
            NR.API.MarkPostAsUnread article.article.id, (data) =>
                article.unread = true
                article.feed.numUnread = article.feed.numUnread + 1
                this.newsFeedView.sort()
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
            this._postPage = 0
            this._seenUnread = 0
            this._maxId = ""
            this._enableFetch = true
            this.fetchMorePosts()

    toggleOptOut: =>
        NR.API.OptOutSharing (not this.localSettings.optedOut), () =>
            this.localSettings.optedOut = not this.localSettings.optedOut
        , this._apiError

    updateFeeds: =>
        NR.API.GetFeeds (feeds) =>
            # Disable sorting until the very end for performance.
            this.newsFeedView.disableSort()
            
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
            toRemove = []
            this.feedList.each (i) ->
                found = false
                for j in feeds
                    if i.id == j.id
                        found = true
                if not found
                    toRemove.push i
            
            for k in toRemove
                this.feedList.remove k
            
            # Reenable sorting.
            this.newsFeedView.enableSort()
        , this._apiError
