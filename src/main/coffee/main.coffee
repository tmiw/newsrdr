class NRApplication extends SimpleMVC.Controller
    @route "/news/:uid", (uid) ->
        # empty for now
    
    constructor: (bootstrappedFeeds) ->
        super()

        NR.API.Initialize()
        
        this.feedList = new SimpleMVC.Collection
        this.view = new NR.Views.NewsFeedListing this.feedList
                
        for i in bootstrappedFeeds
            feed = new NR.Models.NewsFeedInfo
            for k,v of i
                feed[k] = v
            this.feedList.add feed
        
        # Set up timer for feed updates (every 5min).
        setInterval updateFeeds, 1000*60*5
        
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
        
bootstrappedFeeds = [{
    feed: {title: "Engadget"}
    numUnread: 5
}]

app = new NRApplication bootstrappedFeeds
app.start()