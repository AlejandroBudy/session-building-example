package com.alejandrobudy.sessionbuildingexample

import cats.implicits._
import cats.Parallel
import cats.effect.implicits._
import cats.effect.{Async, IO, IOApp, MonadCancelThrow}
import com.alejandrobudy.sessionbuildingexample.domain.{Session, Subscription}
import com.alejandrobudy.sessionbuildingexample.repositories.{
  ProfileRepository,
  SubscriptionsRepository
}
import com.alejandrobudy.sessionbuildingexample.storage.BackingStore

import java.time.Instant
import java.util.UUID

object domain {
  case class Profile(id: String, profileName: String)
  case class Subscriptions(l: List[Subscription])
  case class Subscription(id: String, channel: String)

  case class Session(
      sessionId: UUID,
      profile: Profile,
      subscriptions: Subscriptions,
      createdAt: Instant
  )
}

object repositories {

  import domain._

  object ProfileRepository {
    def fetchProfile: IO[Profile] = IO.pure(Profile("profile-001", "Alejandro"))
  }

  object SubscriptionsRepository {
    def fetch: IO[Subscriptions] =
      IO.pure(
        Subscriptions(
          List(
            Subscription("sub-001", "Ibai"),
            Subscription("sub-001", "Sezar blue")
          )
        )
      )
  }
}

object storage {
  object BackingStore {
    def save(session: Session): IO[Unit]   = IO.unit
    def revoke(session: Session): IO[Unit] = IO.unit
  }
}

object Service {
  def create: IO[Session] =
    for {
      session <- gatherInfo
      _       <- BackingStore.save(session)
    } yield session

  def updateSubscriptions(session: Session): IO[Session] = {
    val revokePreviousSession =
      BackingStore.revoke(session)

    for {
      _ <- revokePreviousSession.start // Don't care the result, we would use retries + logging
      newSession <- gatherInfo
      _          <- BackingStore.save(newSession)
    } yield newSession
  }

  private def gatherInfo: IO[Session] = (
    IO.delay(UUID.randomUUID()),
    ProfileRepository.fetchProfile,
    SubscriptionsRepository.fetch,
    IO.delay(Instant.now())
  ).parMapN(Session.apply)
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = Service.create >> Service.updateSubscriptions
}
