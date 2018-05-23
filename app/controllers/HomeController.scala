package controllers

import javax.inject._
import play.api.mvc._
import play.api.Configuration
import play.api.libs.json._
import scalaj.http.Http
import models.PRDetails
import scala.collection.mutable.ArrayBuffer

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(config: Configuration, cc: ControllerComponents) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {

    val request = Http(s"https://api.github.com/teams/${config.get[Int]("github.team_id")}/repos?per_page=1000")
      .header("Accept", "application/vnd.github.hellcat-preview+json")
      .header("Authorization", s"token ${config.get[String]("github.accesstoken")}").asString
    //println(request.headers)

    //println(request.body)

    val req = request.body

    val jsn = Json.parse(req)

    val size = jsn.as[JsArray].value.size

    println(s"Number of Repos $size")
    val arrayBuffer = ArrayBuffer[PRDetails]()

    for (item <- jsn.as[JsArray].value){
      val repoName = (item \ "name").as[String]
      val request2 = Http(s"https://api.github.com/repos/hmrc/$repoName/pulls")
        .header("Accept", "application/vnd.github.symmetra-preview+json")
        .header("Authorization", s"token ${config.get[String]("github.accesstoken")}").asString

      val prs = Json.parse(request2.body).as[JsArray].value

      if (prs.nonEmpty){
        for (pr <- prs){

          if(!config.underlying.getStringList("github.repo_blacklist").contains(repoName) || (pr \ "title").as[String].contains(config.get[String]("github.jira_code"))) {
            arrayBuffer += PRDetails(
              repoName,
              (pr \ "title").as[String],
              (pr \ "html_url").as[String],
              (pr \ "created_at").as[String],
              (pr \ "updated_at").as[String]
            )
          }
        }

      }

    }

    Ok(views.html.index(arrayBuffer))
  }

}
