package zombie.roadcraft.physics;

/** Dependency-free executable tests for the pure physics core. */
public final class DrivetrainModelTest {
    private static final PhysicsSettings SETTINGS = PhysicsSettings.standard();
    private static int assertions;

    private DrivetrainModelTest() {
    }

    public static void main(String[] args) {
        torqueCurvePeaksAndCutsAtRedline();
        automaticAndManualGearSelectionDiffer();
        closedLoopAutomaticGearboxTraversesEveryGear();
        b42VehicleProfilesHaveDistinctLaunchAndAcceleration();
        closedLoopBehaviorIsStableAcrossFrameRates();
        converterMultipliesTorqueOnlyWhileSlipping();
        manualNeutralAndReverseRemainSelected();
        manualHighGearLaunchBogsInsteadOfMatchingFirst();
        badTiresWeatherAndOffroadReduceTraction();
        burnoutBuildsAndThenRecoversWheelSlip();
        serviceAndParkingBrakesCanLockRearWheels();
        serviceBrakeStopsWithoutDriveOverlap();
        dragSlowsACoastingVehicle();
        inGearCoastAddsEngineBraking();
        steeringFadesWithSpeed();
        reverseSpeedIsCapped();
        deltaAndNonFiniteValuesAreSanitized();
        deterministicReplayIsBitStable();
        extremeInputsStayFiniteAndBounded();
        System.out.println("DrivetrainModelTest: " + assertions + " assertions passed");
    }

    private static void torqueCurvePeaksAndCutsAtRedline() {
        double idle = DrivetrainModel.torqueAtRpm(SETTINGS, SETTINGS.engine().idleRpm());
        double peak = DrivetrainModel.torqueAtRpm(SETTINGS, SETTINGS.engine().peakTorqueRpm());
        double redline = DrivetrainModel.torqueAtRpm(SETTINGS, SETTINGS.engine().redlineRpm());
        double aboveRedline = DrivetrainModel.torqueAtRpm(SETTINGS, SETTINGS.engine().redlineRpm() * 1.2);

        check(idle > 0.0, "idle torque must be positive");
        check(peak > idle, "the torque curve must rise toward its peak");
        close(SETTINGS.engine().peakTorqueNm(), peak, 1.0e-9, "configured peak torque");
        close(0.0, redline, 0.0, "redline fuel cut");
        close(0.0, aboveRedline, 0.0, "above-redline fuel cut");
    }

    private static void automaticAndManualGearSelectionDiffer() {
        VehicleState highRpmFirst = new VehicleState(46.0, 6_400.0, 1, 0.0);
        VehicleInput automaticDrive = input(0.6, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0);
        VehicleInput manualFirst = input(0.6, 0.0, 0.0, 0.0, false, 1, 1.0, 0.0, 0.0);

        VehicleOutput automatic = DrivetrainModel.step(SETTINGS, highRpmFirst, automaticDrive, 1.0 / 60.0);
        VehicleOutput manual = DrivetrainModel.step(SETTINGS, highRpmFirst, manualFirst, 1.0 / 60.0);
        check(automatic.state().gear() == 2, "automatic gearbox must upshift");
        check(automatic.shifted(), "automatic shift must be reported");
        check(manual.state().gear() == 1, "manual gearbox must honor the requested gear");
        check(!manual.shifted(), "manual hold must not report a shift");

        VehicleState lowRpmFourth = new VehicleState(5.0, 1_200.0, 4, 0.0);
        VehicleOutput downshift = DrivetrainModel.step(SETTINGS, lowRpmFourth, automaticDrive, 1.0 / 60.0);
        check(downshift.state().gear() == 3, "automatic gearbox must downshift without over-revving");
    }

    private static void closedLoopAutomaticGearboxTraversesEveryGear() {
        assertAutomaticRun("compact", balancedSettings(1_050.0, 190.0, 100.0, 0.70));
        assertAutomaticRun("standard", balancedSettings(1_325.0, 260.0, 120.0, 1.00));
        assertAutomaticRun("heavy", balancedSettings(2_600.0, 520.0, 100.0, 1.50));
        assertAutomaticRun("sport", balancedSettings(1_100.0, 360.0, 160.0, 0.70));
    }

    private static void assertAutomaticRun(String name, PhysicsSettings settings) {
        VehicleState state = VehicleState.stopped(settings);
        boolean[] seen = new boolean[settings.transmission().forwardGearCount() + 1];
        int previousGear = 1;
        double shiftOneToTwoKph = Double.NaN;
        double minimumFirstGearDriveN = Double.POSITIVE_INFINITY;
        for (int frame = 0; frame < 90 * 60; frame++) {
            VehicleOutput output = DrivetrainModel.step(
                    settings,
                    state,
                    input(1.0, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0),
                    1.0 / 60.0);
            state = output.state();
            int gear = state.gear();
            seen[gear] = true;
            check(gear >= previousGear, name + " automatic must not hunt downward under full throttle");
            if (gear == 1 && state.speedMps() > 5.0) {
                minimumFirstGearDriveN = Math.min(
                        minimumFirstGearDriveN,
                        Math.abs(output.appliedDriveForceN()));
            }
            if (previousGear == 1 && gear == 2) {
                shiftOneToTwoKph = state.speedMps() * 3.6;
            }
            previousGear = gear;
        }

        for (int gear = 1; gear < seen.length; gear++) {
            check(seen[gear], name + " automatic must reach gear " + gear + " in a closed-loop run");
        }
        check(shiftOneToTwoKph > 0.20 * maximumSpeedKph(settings)
                        && shiftOneToTwoKph < 0.45 * maximumSpeedKph(settings),
                name + " first upshift must occur at a plausible road speed");
        check(minimumFirstGearDriveN > 250.0,
                name + " drive force must not collapse before the first upshift");
        check(state.speedMps() * 3.6 > maximumSpeedKph(settings) * 0.70,
                name + " must accelerate through most of its configured speed range");
        check(state.speedMps() * 3.6 < maximumSpeedKph(settings) * 1.20,
                name + " must not materially exceed its configured speed range");
    }

    private static void b42VehicleProfilesHaveDistinctLaunchAndAcceleration() {
        ProfileRun heavyVan = runProfile(runtimeProfileSettings(
                816.0, 3_700.0, 65.0, 2, 4), 2);
        ProfileRun standardCar = runProfile(runtimeProfileSettings(
                800.0, 4_000.0, 90.0, 1, 4), 1);
        ProfileRun sportCar = runProfile(runtimeProfileSettings(
                800.0, 5_700.0, 120.0, 3, 5), 3);

        System.out.println("B42 profile calibration: heavy=" + heavyVan
                + " standard=" + standardCar
                + " sport=" + sportCar);

        check(heavyVan.speedAtOneSecondKph() < 15.0,
                "heavy van must launch progressively instead of reaching road speed in one second");
        check(standardCar.speedAtOneSecondKph() < 22.0,
                "standard car must not receive an instantaneous full-torque launch");
        check(sportCar.speedAtOneSecondKph() < 35.0,
                "sport launch may be quick but must still take measurable time");
        check(sportCar.zeroToSixtySeconds() > 5.50,
                "sport acceleration must remain roughly twenty-five percent below 0.4.2");

        check(sportCar.zeroToSixtySeconds() < standardCar.zeroToSixtySeconds(),
                "sport car must reach 60 km/h before a standard car");
        check(standardCar.zeroToSixtySeconds() < heavyVan.zeroToSixtySeconds(),
                "standard car must reach 60 km/h before a heavy van");
        check(standardCar.zeroToSixtySeconds() - sportCar.zeroToSixtySeconds() > 1.0,
                "sport and standard acceleration must be perceptibly different");
        check(heavyVan.zeroToSixtySeconds() - standardCar.zeroToSixtySeconds() > 1.0,
                "heavy and standard acceleration must be perceptibly different");

        check(sportCar.burnoutFrames() > 0,
                "powerful sport launch must be able to exceed available tire grip");
        check(heavyVan.burnoutFrames() == 0,
                "stock heavy profile must not spin on every dry-road launch");
        check(standardCar.burnoutFrames() > 0,
                "standard profile must now permit a short dry-road burnout");
        check(sportCar.burnoutFrames() > standardCar.burnoutFrames(),
                "sport dry-road burnout must remain stronger than standard");
        check(sportCar.maximumWheelSlip() > heavyVan.maximumWheelSlip() + 0.10,
                "sport launch must spin materially more than a heavy van");
        check(sportCar.speedAtTenSecondsKph() > standardCar.speedAtTenSecondsKph()
                        && standardCar.speedAtTenSecondsKph() > heavyVan.speedAtTenSecondsKph(),
                "vehicle classes must remain separated after the launch phase");
    }

    private static ProfileRun runProfile(PhysicsSettings settings, int mechanicType) {
        VehicleState state = VehicleState.stopped(settings);
        double throttle = 0.0;
        double speedAtOneSecondKph = 0.0;
        double speedAtTenSecondsKph = 0.0;
        double zeroToSixtySeconds = Double.POSITIVE_INFINITY;
        double maximumWheelSlip = 0.0;
        int burnoutFrames = 0;
        for (int frame = 0; frame < 30 * 60; frame++) {
            throttle = RoadcraftCalibration.throttleStep(
                    throttle, 1.0, mechanicType, 1.0 / 60.0);
            VehicleOutput output = DrivetrainModel.step(
                    settings,
                    state,
                    input(throttle, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0),
                    1.0 / 60.0);
            state = output.state();
            double speedKph = state.speedMps() * 3.6;
            maximumWheelSlip = Math.max(maximumWheelSlip, state.wheelSlip());
            if (output.burnout()) {
                burnoutFrames++;
            }
            if (frame == 59) {
                speedAtOneSecondKph = speedKph;
            }
            if (frame == 599) {
                speedAtTenSecondsKph = speedKph;
            }
            if (!Double.isFinite(zeroToSixtySeconds) && speedKph >= 60.0) {
                zeroToSixtySeconds = (frame + 1) / 60.0;
            }
        }
        return new ProfileRun(
                speedAtOneSecondKph,
                speedAtTenSecondsKph,
                zeroToSixtySeconds,
                maximumWheelSlip,
                burnoutFrames);
    }

    private static PhysicsSettings runtimeProfileSettings(
            double massKg,
            double engineForce,
            double maximumSpeedKph,
            int mechanicType,
            int gearCount) {
        PhysicsSettings baseline = PhysicsSettings.standard();
        double redlineRpm = 4_500.0;
        double wheelRadiusMeters = 0.32;
        double peakTorqueNm = RoadcraftCalibration.peakTorqueNm(
                engineForce, mechanicType, 1.0);
        double aero = mechanicType == 3 ? 0.725 : mechanicType == 2 ? 1.525 : 1.025;
        return new PhysicsSettings(
                massKg,
                wheelRadiusMeters,
                40.0 / 3.6,
                new PhysicsSettings.Engine(
                        800.0,
                        redlineRpm * RoadcraftCalibration.peakTorqueRpmFraction(mechanicType),
                        redlineRpm,
                        peakTorqueNm * RoadcraftCalibration.idleTorqueFraction(mechanicType),
                        peakTorqueNm,
                        peakTorqueNm * RoadcraftCalibration.redlineTorqueFraction(mechanicType),
                        10_000.0),
                new PhysicsSettings.Transmission(
                        RoadcraftCalibration.forwardGearRatios(gearCount),
                        RoadcraftCalibration.reverseGearRatio(gearCount),
                        RoadcraftCalibration.finalDriveRatio(
                                redlineRpm, maximumSpeedKph, wheelRadiusMeters),
                        0.88,
                        redlineRpm * 0.855556,
                        redlineRpm * 0.966667),
                new PhysicsSettings.Converter(
                        RoadcraftCalibration.torqueConverterMultiplier(2.5, mechanicType),
                        2_000.0),
                new PhysicsSettings.Grip(
                        1.0,
                        0.0,
                        1.0,
                        0.50,
                        RoadcraftCalibration.drivenWeightFraction(mechanicType),
                        4.6,
                        2.1),
                baseline.brakes(),
                new PhysicsSettings.Resistance(aero, 0.0145),
                baseline.steering(),
                baseline.timeStep());
    }

    private static void closedLoopBehaviorIsStableAcrossFrameRates() {
        PhysicsSettings settings = balancedSettings(1_325.0, 260.0, 120.0, 1.0);
        VehicleState atThirty = simulate(settings, 30, 24.0, VehicleState.stopped(settings),
                input(1.0, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0));
        VehicleState atSixty = simulate(settings, 60, 24.0, VehicleState.stopped(settings),
                input(1.0, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0));
        VehicleState at144 = simulate(settings, 144, 24.0, VehicleState.stopped(settings),
                input(1.0, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0));

        close(atSixty.speedMps(), atThirty.speedMps(), 0.60,
                "30 FPS acceleration must stay close to 60 FPS");
        close(atSixty.speedMps(), at144.speedMps(), 0.60,
                "144 FPS acceleration must stay close to 60 FPS");
        check(atThirty.gear() == atSixty.gear() && atSixty.gear() == at144.gear(),
                "automatic gear after equal simulated time must be frame-rate independent");
    }

    private static void converterMultipliesTorqueOnlyWhileSlipping() {
        VehicleInput automaticLaunch = input(1.0, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0);
        VehicleOutput automatic = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(0.0, SETTINGS.engine().idleRpm(), 1, 0.0),
                automaticLaunch,
                1.0 / 60.0);
        check(automatic.converterSlip() > 0.8, "launch must create converter slip");
        check(automatic.converterTorqueMultiplier() > 1.5, "launch slip must multiply torque");
        check(automatic.driveDeliveryFactor() < 0.67,
                "slipping converter must not turn all multiplied torque into body acceleration");
        check(Math.abs(automatic.appliedDriveForceN())
                        < Math.min(Math.abs(automatic.rawDriveForceN()), automatic.tractionLimitN()),
                "automatic launch must retain multiplied raw force for slip while limiting road delivery");

        VehicleInput manualLaunch = input(1.0, 0.0, 0.0, 0.0, false, 1, 1.0, 0.0, 0.0);
        VehicleOutput manual = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(0.0, SETTINGS.engine().idleRpm(), 1, 0.0),
                manualLaunch,
                1.0 / 60.0);
        close(0.0, manual.converterSlip(), 0.0, "manual transmission has no torque converter slip");
        close(1.0, manual.converterTorqueMultiplier(), 0.0, "manual transmission has no converter multiplication");
        close(1.0, manual.driveDeliveryFactor(), 0.0,
                "manual road delivery must remain unchanged");
    }

    private static void manualNeutralAndReverseRemainSelected() {
        VehicleOutput neutral = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(0.0, SETTINGS.engine().idleRpm(), 0, 0.0),
                input(1.0, 0.0, 0.0, 0.0, false, 0, 1.0, 0.0, 0.0),
                1.0 / 60.0);
        check(neutral.state().gear() == 0, "manual neutral must remain neutral under throttle");
        close(0.0, neutral.appliedDriveForceN(), 0.0, "manual neutral cannot apply wheel force");

        VehicleOutput reverse = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(0.0, SETTINGS.engine().idleRpm(), -1, 0.0),
                input(1.0, 0.0, 0.0, 0.0, false, -1, 1.0, 0.0, 0.0),
                1.0 / 60.0);
        check(reverse.state().gear() == -1, "manual reverse must remain selected near rest");
        check(reverse.appliedDriveForceN() < 0.0, "manual reverse must apply signed reverse force");
    }

    private static void manualHighGearLaunchBogsInsteadOfMatchingFirst() {
        PhysicsSettings settings = balancedSettings(1_325.0, 260.0, 120.0, 1.0);
        VehicleState first = simulate(
                settings,
                60,
                6.0,
                VehicleState.stopped(settings),
                input(1.0, 0.0, 0.0, 0.0, false, 1, 1.0, 0.0, 0.0));
        VehicleState fifth = simulate(
                settings,
                60,
                6.0,
                new VehicleState(0.0, settings.engine().idleRpm(), 5, 0.0),
                input(1.0, 0.0, 0.0, 0.0, false, 5, 1.0, 0.0, 0.0));

        check(first.speedMps() * 3.6 > 25.0,
                "manual first gear must launch an ordinary car decisively");
        check(fifth.speedMps() * 3.6 < 5.0,
                "manual fifth gear must bog near standstill");
        check(first.speedMps() > fifth.speedMps() * 8.0,
                "first-gear launch must be materially stronger than fifth");
    }

    private static void badTiresWeatherAndOffroadReduceTraction() {
        VehicleState state = new VehicleState(7.0, 3_000.0, 1, 0.0);
        VehicleOutput dry = DrivetrainModel.step(
                SETTINGS,
                state,
                input(1.0, 0.0, 0.0, 0.0, false, 1, 1.0, 0.0, 0.0),
                1.0 / 60.0);
        VehicleOutput poor = DrivetrainModel.step(
                SETTINGS,
                state,
                input(1.0, 0.0, 0.0, 0.0, false, 1, 0.15, 1.0, 1.0),
                1.0 / 60.0);

        check(poor.effectiveGripCoefficient() < dry.effectiveGripCoefficient(),
                "tire, weather and terrain modifiers must reduce grip");
        check(poor.tractionLimitN() < dry.tractionLimitN(), "reduced grip must lower traction limit");
        check(Math.abs(poor.appliedDriveForceN()) < Math.abs(dry.appliedDriveForceN()),
                "reduced traction must lower applied drive force");
    }

    private static void burnoutBuildsAndThenRecoversWheelSlip() {
        VehicleInput launch = input(1.0, 0.0, 0.0, 0.0, true, 1, 0.25, 1.0, 1.0);
        VehicleOutput spinning = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(0.0, SETTINGS.engine().idleRpm(), 1, 0.0),
                launch,
                0.1);
        check(spinning.burnout(), "excess launch force must be classified as burnout");
        check(spinning.state().wheelSlip() > 0.0, "burnout must build wheel slip");

        VehicleOutput recovered = DrivetrainModel.step(
                SETTINGS,
                spinning.state(),
                input(0.0, 0.0, 0.0, 0.0, true, 1, 1.0, 0.0, 0.0),
                0.1);
        check(!recovered.burnout(), "closed throttle must stop burnout");
        check(recovered.state().wheelSlip() < spinning.state().wheelSlip(),
                "wheel slip must recover toward zero");
    }

    private static void serviceAndParkingBrakesCanLockRearWheels() {
        VehicleState moving = new VehicleState(18.0, 2_600.0, 3, 0.0);
        VehicleOutput serviceLock = DrivetrainModel.step(
                SETTINGS,
                moving,
                input(0.0, 1.0, 0.0, 0.0, false, 3, 1.0, 1.0, 1.0),
                1.0 / 60.0);
        check(serviceLock.rearWheelsLocked(), "hard service braking must lock the rear axle on poor grip");
        check(serviceLock.serviceBrakeForceN() > 0.0, "service brake force must be applied");

        VehicleOutput parkingLock = DrivetrainModel.step(
                SETTINGS,
                moving,
                input(0.0, 0.0, 1.0, 0.0, false, 3, 1.0, 0.0, 0.0),
                1.0 / 60.0);
        check(parkingLock.rearWheelsLocked(), "parking brake must be able to lock the rear axle");
        check(parkingLock.parkingBrakeForceN() > 0.0, "parking brake force must be applied");
    }

    private static void serviceBrakeStopsWithoutDriveOverlap() {
        double speedMps = 80.0 / 3.6;
        int gear = 3;
        VehicleState state = new VehicleState(
                speedMps,
                DrivetrainModel.coupledEngineRpm(SETTINGS, speedMps, gear),
                gear,
                0.0);
        int frames = 0;
        while (state.speedMps() > 0.01 && frames < 10 * 60) {
            VehicleOutput output = DrivetrainModel.step(
                    SETTINGS,
                    state,
                    input(1.0, 1.0, 0.0, 0.0, false, gear, 1.0, 0.0, 0.0),
                    1.0 / 60.0);
            close(0.0, output.engineTorqueNm(), 0.0,
                    "service brake must suppress engine torque even with malformed throttle overlap");
            close(0.0, output.appliedDriveForceN(), 0.0,
                    "service brake must never overlap applied drive force");
            check(output.state().speedMps() >= 0.0, "braking must not reverse the vehicle");
            state = output.state();
            frames++;
        }
        check(state.speedMps() <= 0.01, "full service brake must stop from 80 km/h");
        check(frames < 8 * 60, "full service brake must stop in under eight seconds");
    }

    private static void dragSlowsACoastingVehicle() {
        VehicleState moving = new VehicleState(35.0, SETTINGS.engine().idleRpm(), 0, 0.0);
        VehicleOutput output = DrivetrainModel.step(SETTINGS, moving, VehicleInput.idle(false), 0.1);
        check(output.aerodynamicDragForceN() > 0.0, "aero drag must be present at speed");
        check(output.rollingDragForceN() > 0.0, "rolling drag must be present at speed");
        check(output.netLongitudinalForceN() < 0.0, "drag force must oppose forward travel");
        check(output.state().speedMps() < moving.speedMps(), "coasting vehicle must slow down");
    }

    private static void inGearCoastAddsEngineBraking() {
        double speedMps = 80.0 / 3.6;
        int gear = 4;
        PhysicsSettings settings = balancedSettings(1_325.0, 260.0, 120.0, 1.0);
        VehicleState initial = new VehicleState(
                speedMps,
                DrivetrainModel.coupledEngineRpm(settings, speedMps, gear),
                gear,
                0.0);
        VehicleOutput firstInGear = DrivetrainModel.step(
                settings,
                initial,
                input(0.0, 0.0, 0.0, 0.0, false, gear, 1.0, 0.0, 0.0),
                1.0 / 60.0);
        check(firstInGear.engineBrakingForceN() > 0.0,
                "released throttle in gear must produce engine braking");
        close(0.0, firstInGear.serviceBrakeForceN(), 0.0,
                "coasting must not invent service braking");

        VehicleState inGear = simulate(
                settings, 60, 10.0, initial,
                input(0.0, 0.0, 0.0, 0.0, false, gear, 1.0, 0.0, 0.0));
        VehicleState neutral = simulate(
                settings, 60, 10.0,
                new VehicleState(speedMps, settings.engine().idleRpm(), 0, 0.0),
                input(0.0, 0.0, 0.0, 0.0, false, 0, 1.0, 0.0, 0.0));
        check(neutral.speedMps() < speedMps, "neutral coasting must lose speed to road load");
        check(neutral.speedMps() > 0.0, "neutral coasting must continue rolling after ten seconds");
        check(inGear.speedMps() < neutral.speedMps(),
                "in-gear coasting must slow more than neutral because of engine braking");
    }

    private static void steeringFadesWithSpeed() {
        VehicleInput fullSteer = input(0.0, 0.0, 0.0, 1.0, false, 0, 1.0, 0.0, 0.0);
        VehicleOutput slow = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(1.0, SETTINGS.engine().idleRpm(), 0, 0.0),
                fullSteer,
                1.0 / 60.0);
        VehicleOutput fast = DrivetrainModel.step(
                SETTINGS,
                new VehicleState(45.0, SETTINGS.engine().idleRpm(), 0, 0.0),
                fullSteer,
                1.0 / 60.0);
        check(Math.abs(fast.steeringAngleRadians()) < Math.abs(slow.steeringAngleRadians()),
                "steering angle must fade with speed");
        check(Math.abs(fast.steeringAngleRadians())
                        >= SETTINGS.steering().maximumAngleRadians()
                        * SETTINGS.steering().minimumHighSpeedFraction(),
                "steering fade must retain the configured high-speed floor");
    }

    private static void reverseSpeedIsCapped() {
        VehicleState state = VehicleState.stopped(SETTINGS);
        VehicleInput reverse = input(1.0, 0.0, 0.0, 0.0, true, -1, 1.0, 0.0, 0.0);
        boolean limiterObserved = false;
        for (int index = 0; index < 1_200; index++) {
            VehicleOutput output = DrivetrainModel.step(SETTINGS, state, reverse, 1.0 / 60.0);
            state = output.state();
            limiterObserved |= output.reverseLimited();
            check(state.speedMps() >= -SETTINGS.reverseSpeedCapMps(), "reverse speed cap must never be exceeded");
        }
        check(limiterObserved, "reverse limiter must engage during a sustained reverse run");
        check(state.speedMps() < -SETTINGS.reverseSpeedCapMps() * 0.75,
                "reverse simulation must approach the configured cap");
    }

    private static void deltaAndNonFiniteValuesAreSanitized() {
        VehicleState invalidState = new VehicleState(Double.NaN, Double.POSITIVE_INFINITY, Integer.MAX_VALUE, Double.NaN);
        VehicleInput invalidInput = input(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NaN,
                true,
                Integer.MAX_VALUE,
                Double.NaN,
                Double.NaN,
                Double.NaN);

        VehicleOutput fallbackDelta = DrivetrainModel.step(SETTINGS, invalidState, invalidInput, Double.NaN);
        close(SETTINGS.timeStep().fallbackSeconds(), fallbackDelta.sanitizedDeltaSeconds(), 0.0,
                "NaN delta must use fallback");
        assertFinite(fallbackDelta);

        VehicleOutput maximumDelta = DrivetrainModel.step(SETTINGS, invalidState, invalidInput, 99_999.0);
        close(SETTINGS.timeStep().maximumSeconds(), maximumDelta.sanitizedDeltaSeconds(), 0.0,
                "huge delta must be clamped");
        assertFinite(maximumDelta);

        VehicleOutput minimumDelta = DrivetrainModel.step(SETTINGS, invalidState, invalidInput, Double.MIN_VALUE);
        close(SETTINGS.timeStep().minimumSeconds(), minimumDelta.sanitizedDeltaSeconds(), 0.0,
                "tiny positive delta must be clamped");
        assertFinite(minimumDelta);

        VehicleOutput negativeDelta = DrivetrainModel.step(SETTINGS, invalidState, invalidInput, -1.0);
        close(SETTINGS.timeStep().fallbackSeconds(), negativeDelta.sanitizedDeltaSeconds(), 0.0,
                "negative delta must use fallback");
        assertFinite(negativeDelta);
    }

    private static void deterministicReplayIsBitStable() {
        VehicleState first = VehicleState.stopped(SETTINGS);
        VehicleState second = VehicleState.stopped(SETTINGS);
        for (int index = 0; index < 800; index++) {
            double phase = (index % 120) / 119.0;
            VehicleInput input = input(
                    index < 500 ? 0.35 + 0.65 * phase : 0.0,
                    index >= 500 ? (index - 500) / 300.0 : 0.0,
                    index > 700 ? 0.3 : 0.0,
                    Math.sin(index * 0.071),
                    true,
                    1,
                    0.8,
                    0.25,
                    index % 200 < 50 ? 0.6 : 0.0);
            VehicleOutput firstOutput = DrivetrainModel.step(SETTINGS, first, input, 1.0 / 60.0);
            VehicleOutput secondOutput = DrivetrainModel.step(SETTINGS, second, input, 1.0 / 60.0);
            check(firstOutput.equals(secondOutput), "identical replay inputs must produce bit-stable outputs");
            first = firstOutput.state();
            second = secondOutput.state();
        }
    }

    private static void extremeInputsStayFiniteAndBounded() {
        VehicleState state = new VehicleState(250.0, SETTINGS.engine().redlineRpm() * 1.25, 99, 1.0);
        for (int index = 0; index < 2_000; index++) {
            double sign = index % 2 == 0 ? 1.0 : -1.0;
            VehicleInput extreme = input(
                    sign * Double.MAX_VALUE,
                    -sign * Double.MAX_VALUE,
                    index % 3 == 0 ? Double.MAX_VALUE : -Double.MAX_VALUE,
                    sign * Double.MAX_VALUE,
                    index % 2 == 0,
                    index % 5 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE,
                    sign * Double.MAX_VALUE,
                    -sign * Double.MAX_VALUE,
                    sign * Double.MAX_VALUE);
            VehicleOutput output = DrivetrainModel.step(
                    SETTINGS,
                    state,
                    extreme,
                    index % 2 == 0 ? Double.MIN_VALUE : Double.MAX_VALUE);
            assertFinite(output);
            check(output.state().wheelSlip() >= 0.0 && output.state().wheelSlip() <= 1.0,
                    "wheel slip must remain normalized");
            check(Math.abs(output.state().speedMps()) <= 250.0, "sanitized speed must remain bounded");
            check(output.state().engineRpm() >= SETTINGS.engine().idleRpm(),
                    "running engine RPM must not fall below idle");
            check(output.state().engineRpm() <= SETTINGS.engine().redlineRpm() * 1.25,
                    "engine RPM clamp must bound pathological inputs");
            state = output.state();
        }
    }

    private static PhysicsSettings balancedSettings(
            double massKg,
            double peakTorqueNm,
            double maximumSpeedKph,
            double aerodynamicDragAreaM2) {
        PhysicsSettings baseline = PhysicsSettings.standard();
        double redlineRpm = 4_500.0;
        double wheelRadiusMeters = 0.32;
        int gearCount = 5;
        return new PhysicsSettings(
                massKg,
                wheelRadiusMeters,
                40.0 / 3.6,
                new PhysicsSettings.Engine(
                        800.0,
                        redlineRpm * 0.50,
                        redlineRpm,
                        peakTorqueNm * 0.36,
                        peakTorqueNm,
                        peakTorqueNm,
                        10_000.0),
                new PhysicsSettings.Transmission(
                        RoadcraftCalibration.forwardGearRatios(gearCount),
                        RoadcraftCalibration.reverseGearRatio(gearCount),
                        RoadcraftCalibration.finalDriveRatio(
                                redlineRpm, maximumSpeedKph, wheelRadiusMeters),
                        0.88,
                        redlineRpm * 0.855556,
                        redlineRpm * 0.966667),
                new PhysicsSettings.Converter(2.5, 2_000.0),
                new PhysicsSettings.Grip(
                        1.0,
                        0.0,
                        1.0,
                        0.50,
                        RoadcraftCalibration.drivenWeightFraction(),
                        4.6,
                        2.1),
                baseline.brakes(),
                new PhysicsSettings.Resistance(aerodynamicDragAreaM2, 0.0145),
                baseline.steering(),
                baseline.timeStep());
    }

    private static double maximumSpeedKph(PhysicsSettings settings) {
        double wheelRpmPerKph = 60.0
                / (3.6 * 2.0 * Math.PI * settings.wheelRadiusMeters());
        double coupledRpmPerKph = wheelRpmPerKph * settings.transmission().finalDriveRatio();
        return 0.95 * settings.engine().redlineRpm() / coupledRpmPerKph;
    }

    private static VehicleState simulate(
            PhysicsSettings settings,
            int framesPerSecond,
            double seconds,
            VehicleState initial,
            VehicleInput input) {
        VehicleState state = initial;
        int frames = (int) Math.round(framesPerSecond * seconds);
        for (int frame = 0; frame < frames; frame++) {
            state = DrivetrainModel.step(settings, state, input, 1.0 / framesPerSecond).state();
        }
        return state;
    }

    private static VehicleInput input(
            double throttle,
            double serviceBrake,
            double parkingBrake,
            double steering,
            boolean automatic,
            int gear,
            double tireCondition,
            double wetness,
            double offroadFraction) {
        return new VehicleInput(
                throttle,
                serviceBrake,
                parkingBrake,
                steering,
                automatic,
                gear,
                tireCondition,
                wetness,
                offroadFraction);
    }

    private static void assertFinite(VehicleOutput output) {
        finite(output.state().speedMps(), "speed");
        finite(output.state().engineRpm(), "RPM");
        finite(output.state().wheelSlip(), "wheel slip");
        finite(output.sanitizedDeltaSeconds(), "delta");
        finite(output.engineTorqueNm(), "engine torque");
        finite(output.converterSlip(), "converter slip");
        finite(output.converterTorqueMultiplier(), "converter multiplier");
        finite(output.rawDriveForceN(), "raw drive force");
        finite(output.appliedDriveForceN(), "applied drive force");
        finite(output.tractionLimitN(), "traction limit");
        finite(output.effectiveGripCoefficient(), "grip coefficient");
        finite(output.serviceBrakeForceN(), "service brake force");
        finite(output.parkingBrakeForceN(), "parking brake force");
        finite(output.aerodynamicDragForceN(), "aero drag");
        finite(output.rollingDragForceN(), "rolling drag");
        finite(output.engineBrakingForceN(), "engine braking");
        finite(output.netLongitudinalForceN(), "net force");
        finite(output.steeringAngleRadians(), "steering angle");
    }

    private static void finite(double value, String message) {
        check(Double.isFinite(value), message + " must be finite, got " + value);
    }

    private static void close(double expected, double actual, double tolerance, String message) {
        check(Math.abs(expected - actual) <= tolerance,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record ProfileRun(
            double speedAtOneSecondKph,
            double speedAtTenSecondsKph,
            double zeroToSixtySeconds,
            double maximumWheelSlip,
            int burnoutFrames) {
    }
}
