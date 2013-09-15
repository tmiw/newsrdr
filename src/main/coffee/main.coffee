class TestModel extends SimpleMVC.Model
    @fields "task"

model = new TestModel

class TestView extends SimpleMVC.View
    @id "task-item"
    @event "click", ".task-name", () ->
        alert "clicked!"
    
    template: (scope) ->
        "<div class='task-name'>#{scope.task}</div>"

view = new TestView
view.model = model

class TestController extends SimpleMVC.Controller
    @urlBase "/test"
    @route "/:id", (id) -> model.task = id
    @route "/", () -> model.task = "none"
    
window.controller = new TestController
window.controller.start()