class NRApplication extends SimpleMVC.Controller
    @route "/news/:uid", (uid) ->
        # empty for now
    
    constructor: (bootstrappedFeeds) ->
        super()

        this.feedList = new SimpleMVC.Collection
        this.view = new NR.Views.NewsFeedListing this.feedList
                
        for i in bootstrappedFeeds
            feed = new NR.Models.NewsFeedInfo
            for k,v of i
                feed[k] = v
            this.feedList.add feed
            
bootstrappedFeeds = [{
    feed: {title: "Engadget"}
    numUnread: 5
}]

app = new NRApplication bootstrappedFeeds
app.start()