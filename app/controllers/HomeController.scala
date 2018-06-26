package controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import javax.inject._
import play.api.mvc._
import play.api.Configuration
import play.api.libs.json._
import scalaj.http.Http
import models.PRDetails
import play.api.i18n._

import scala.collection.mutable.ArrayBuffer


@Singleton
class HomeController @Inject()(config: Configuration, cc: ControllerComponents, messagesApi: MessagesApi) extends AbstractController(cc)  {

  def index = Action {

    val lang: Lang = new Lang(Locale.ENGLISH)
    val messages: Messages = MessagesImpl(lang, messagesApi)


    val getReposForTeamRequest = Http(s"https://api.github.com/teams/${config.get[Int]("github.team_id")}/repos?per_page=1000")
      .header("Accept", "application/vnd.github.hellcat-preview+json")
      .header("Authorization", s"token ${config.get[String]("github.accesstoken")}").asString

    val getReposForTeamJson = Json.parse(getReposForTeamRequest.body)

    val noOfTeams = getReposForTeamJson.as[JsArray].value.size

    val arrayBuffer = ArrayBuffer[PRDetails]()

    for (repo <- getReposForTeamJson.as[JsArray].value){
      val repoName = (repo \ "name").as[String]
      val getRepoPrsRequest = Http(s"https://api.github.com/repos/hmrc/$repoName/pulls")
        .header("Accept", "application/vnd.github.symmetra-preview+json")
        .header("Authorization", s"token ${config.get[String]("github.accesstoken")}").asString

      val prs = Json.parse(getRepoPrsRequest.body).as[JsArray].value

      if (prs.nonEmpty){
        for (pr <- prs){

          if(!config.underlying.getStringList("github.repo_blacklist").contains(repoName) || (pr \ "title").as[String].contains(config.get[String]("github.jira_code"))) {
            arrayBuffer += PRDetails(
              repoName,
              (pr \ "title").as[String],
              (pr \ "html_url").as[String],
              getCleanDate((pr \ "created_at").as[String]),
              getDaysSince((pr \ "created_at").as[String]),
              getCleanDate((pr \ "updated_at").as[String]),
              getDaysSince((pr \ "updated_at").as[String])
            )
          }
        }
      }
    }
    Ok(views.html.index(config.get[String]("team_name"), noOfTeams,arrayBuffer.sortWith(_.repo < _.repo), messages))
  }

  def getCleanDate(date: String): String = {
    val originalFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val ouputFormat = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm")

    ouputFormat.format(originalFormat.parse(date))
  }

  def getDaysSince(date: String): Long = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val convertedDate = LocalDate.parse(date, formatter)
    val dateNow = LocalDate.now()
    dateNow.toEpochDay - convertedDate.toEpochDay
  }

}
