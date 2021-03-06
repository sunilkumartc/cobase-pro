package cobase.play.group

import javax.inject.Inject

import cobase.group.{GroupService, Group}
import cobase.post.PostService
import cobase.twitter.{Tweet, TwitterService}
import cobase.user._
import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import play.api.i18n.Messages

import scala.concurrent.Future
import java.util.UUID
import play.api.libs.json._

class GroupController @Inject() (implicit val env: Environment[User, SessionAuthenticator],
                                 groupService: GroupService,
                                 postService: PostService,
                                 subscriptionService: SubscriptionService,
                                 twitterService: TwitterService)
  extends Silhouette[User, SessionAuthenticator] {

  def addGroupForm() = SecuredAction.async { implicit request =>
    val groupLinks = groupService.findGroupLinks

    Future.successful(Ok(views.html.newGroup(request.identity, groupLinks, GroupForm.form)))
  }

  def addGroup() = SecuredAction.async { implicit request =>
    Future.successful(
      GroupForm.form.bindFromRequest.fold(
        formWithErrors => {
          Ok(
            views.html.newGroup(request.identity, groupService.findGroupLinks, formWithErrors)
          )
        },
        data => {
          groupService.add(
            Group(UUID.randomUUID, data.title, data.tags)
          )

          Redirect(
            cobase.play.user.routes.ApplicationController.index()).flashing("info" -> Messages("group.created")
          )
        }
      )
    )
  }

  def editGroupForm(groupId: UUID) = SecuredAction.async { implicit request =>
    Future.successful(
      groupService.findById(groupId) match {
        case Some(group) =>
          val filledForm = GroupForm.form.fill(GroupFormData(group.title, group.tags))

          Ok(views.html.editGroup(request.identity, groupService.findGroupLinks, filledForm, group))

        case None => NotFound(views.html.notFound(
          request.identity,
          groupService.findGroupLinks,
          "Group with id " + groupId + " not found"
        ))
      }
    )
  }

  def editGroup(groupId: UUID) = SecuredAction.async { implicit request =>
    Future.successful(
      groupService.findById(groupId) match {
        case Some(group) =>
          GroupForm.form.bindFromRequest.fold(
            formWithErrors => {
              Ok(
                views.html.editGroup(request.identity, groupService.findGroupLinks, formWithErrors, group)
              )
            },
            data => {
              groupService.update(Group(group.id, data.title, data.tags))

              Redirect(
                cobase.play.post.routes.PostController.viewPosts(group.id)).flashing("info" -> Messages("group.updated")
              )
            }
          )

        case None => NotFound(views.html.notFound(
          request.identity,
          groupService.findGroupLinks,
          "Group with id " + groupId + " not found"
        ))
      }
    )
  }

  def subscribe(groupId: UUID) = SecuredAction.async { implicit request =>
    Future.successful(
      groupService.findById(groupId) match {
        case Some(group) =>
          subscriptionService.subscribeUserToGroup(request.identity, group)

          Redirect(
            cobase.play.user.routes.ApplicationController.index()).flashing("info" -> Messages("group.subscribe")
          )

        case None => NotFound(views.html.notFound(
          request.identity,
          groupService.findGroupLinks,
          "Group with id " + groupId + " not found"
        ))
      }
    )
  }

  def unsubscribe(groupId: UUID) = SecuredAction.async { implicit request =>
    Future.successful(
      groupService.findById(groupId) match {
        case Some(group) =>
          subscriptionService.unsubscribeUserFromGroup(request.identity, group)

          Redirect(
            cobase.play.user.routes.ApplicationController.index()).flashing("info" -> Messages("group.unsubscribe")
          )

        case None => NotFound(views.html.notFound(
          request.identity,
          groupService.findGroupLinks,
          "Group with id " + groupId + " not found"
        ))
      }
    )
  }

  def getTweetsForGroup(groupId: UUID) = SecuredAction.async { implicit request =>
    implicit val twitterFeedItemWrites = Json.writes[Tweet]

    Future.successful {
      val tweets = for {
        group <- groupService.findById(groupId)
        tweets <- twitterService.getGroupTweets(group.tags)
      } yield (tweets)

      tweets match {
        case Some(_) => Ok(
          Json.toJson(
            tweets
          )
        )
        case None => NotFound("No tweets found")
      }
    }
  }

}
