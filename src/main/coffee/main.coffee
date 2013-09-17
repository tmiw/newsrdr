feed = new NR.Models.NewsFeedInfo
feed.feed = {title: "Engadget"}
feed.numUnread = 5

model = new SimpleMVC.Collection
view = new NR.Views.NewsFeedListing model
model.add feed