package ca.team3161.robot;

import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import com.kauailabs.navx.frc.AHRS;

import ca.team3161.lib.utils.controls.InvertedJoystickMode;
import ca.team3161.lib.utils.controls.LogitechDualAction;
import ca.team3161.lib.utils.controls.LogitechDualAction.LogitechAxis;
import ca.team3161.lib.utils.controls.LogitechDualAction.LogitechControl;
import ca.team3161.lib.utils.controls.SquaredJoystickMode;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.I2C;
import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.PIDController;
import edu.wpi.first.wpilibj.PIDOutput;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.VictorSP;
import edu.wpi.first.wpilibj.drive.MecanumDrive;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class Robot extends IterativeRobot implements PIDOutput 
{ 

	//This is declaring the Motors with the corresponding Controllers
	private WPI_TalonSRX frontLeftDrive = new WPI_TalonSRX(0);
	private WPI_TalonSRX frontRightDrive = new WPI_TalonSRX(1);
	private WPI_TalonSRX backLeftDrive = new WPI_TalonSRX(2);
	private WPI_TalonSRX backRightDrive = new WPI_TalonSRX(3);
	private VictorSP IntakeL = new VictorSP (0);
	private VictorSP IntakeR = new VictorSP (1);
	private VictorSP pivot = new VictorSP (2);
	private WPI_TalonSRX leftElevator = new WPI_TalonSRX (4);
	private WPI_TalonSRX rightElevator = new WPI_TalonSRX (5);

	//Declaring the way the robot will drive - RoboDrive class
	private MecanumDrive drivetrain;

	//Declaring the AHRS class to get gyro headings
	private AHRS ahrs;
	private double angle;

	//This is declaring both Controllers 
	private LogitechDualAction driverPad = new LogitechDualAction(0);
	private LogitechDualAction operatorPad = new LogitechDualAction(1);

	//For PID
	double rotateToAngleRate;
	double P = 0.02;
	double I = 0.00;
	double D = 0.06;
	float kToleranceDegrees = 2;
	PIDController turnController;
	boolean rotateToAngle;
	double currentRotationRate;

	//Configures all the Joysticks and Buttons for both controllers
	double leftStickX;
	double leftStickY;	 
	double rightStickX;
	double rightStickY;
	double leftStickY_Operator;
	boolean getButtonSTART;
	boolean getButtonY;
	boolean getButtonX;
	boolean getButtonA;
	boolean getButtonB_Operator;
	boolean getButtonB;
	boolean getButtonLB_Operator;
	boolean getButtonLT_Operator;
	boolean getButotnRB_Operator;

	//need to set variables to use PCM
	private Compressor air = new Compressor(0);
	private boolean pressureSwitch;

	//Declaring Buttons for the pistons
	private DoubleSolenoid claw = new DoubleSolenoid (1,0);

	//Declaring a string to get the switch/scale positioning for each alliance
	String gameData;

	//Declaring a timer for autonomous timings
	Timer autoTimer = new Timer();

	//Declaring positions for starting autonomous
	boolean MIDDLE, LEFT, RIGHT, DO_NOTHING;

	public void robotInit() 
	{
		frontLeftDrive.setInverted(false);
		frontRightDrive.setInverted(false);
		backRightDrive.setInverted(false);
		backLeftDrive.setInverted(false);

		//Reads in gyro readings from I2C connections
		ahrs = new AHRS(I2C.Port.kOnboard);

		//Initiate the RoboDrive class, so that drivetrain variable can be used with the talons - driving controller
		drivetrain = new MecanumDrive(frontLeftDrive, backLeftDrive, frontRightDrive, backRightDrive);

		//Resetting the gyro reading for the rest of run time
		ahrs.reset();

		//Executes PID calculations
		turnController = new PIDController(P, I, D, ahrs, this);
		turnController.setInputRange(-180.0f,  180.0f);
		turnController.setOutputRange(-1.0, 1.0);
		turnController.setAbsoluteTolerance(kToleranceDegrees);
		turnController.setContinuous(true);

		//Initiate Encoders for all wheels
		//NEED TO CONFIGURE CHANNELS TO EACH ENCODER
		
		//Encoder fLeft = new Encoder (0, 1);
		/*
		Encoder fRight = new Encoder (0, 1, false, Encoder.EncodingType.k4X);
		Encoder bLeft = new Encoder (0, 1, false, Encoder.EncodingType.k4X);
		Encoder bRight = new Encoder (0, 1, false, Encoder.EncodingType.k4X);
		 */

		//The robot's claw is set to closed because it will be holding a cube when turned on
		ClawClose();

		//with proper wiring, calibrates the talons using polarity inversions
		driverPad.setMode(LogitechControl.LEFT_STICK, LogitechAxis.X, new SquaredJoystickMode());
		driverPad.setMode(LogitechControl.LEFT_STICK, LogitechAxis.Y, new InvertedJoystickMode().andThen(new SquaredJoystickMode()));
		driverPad.setMode(LogitechControl.RIGHT_STICK, LogitechAxis.X, new SquaredJoystickMode());
	}
	
	public void disabledPeriodic() {
		SmartDashboard.putNumber("gyro", ahrs.getYaw());
	}

	public void autonomousInit() 
	{
		ahrs.reset();
		gameData = DriverStation.getInstance().getGameSpecificMessage();
	}

	public void autonomousPeriodic()
	{
		//get air for pneumatics
		pressureSwitch = air.getPressureSwitchValue();
		if (!pressureSwitch) 
		{
			air.setClosedLoopControl(true);
		}
		drivetrain.driveCartesian(-0.5, 0.0, forwardPID(),0.0 );

		//stop taking air when pneumatics reaches 120 psi
		if (pressureSwitch) 
		{
			air.setClosedLoopControl(false);
		}
	}

	public void teleopPeriodic() 
	{
		
		leftStickX = driverPad.getValue(LogitechDualAction.LogitechControl.LEFT_STICK, LogitechDualAction.LogitechAxis.X);
		leftStickY = driverPad.getValue(LogitechDualAction.LogitechControl.LEFT_STICK, LogitechDualAction.LogitechAxis.Y);
		rightStickX = driverPad.getValue(LogitechDualAction.LogitechControl.RIGHT_STICK, LogitechDualAction.LogitechAxis.X);
		rightStickY = operatorPad.getValue(LogitechDualAction.LogitechControl.RIGHT_STICK, LogitechDualAction.LogitechAxis.Y);
		getButtonSTART = driverPad.getButton(LogitechDualAction.LogitechButton.START);
		getButtonY = driverPad.getButton(LogitechDualAction.LogitechButton.Y);
		getButtonX = driverPad.getButton(LogitechDualAction.LogitechButton.X);
		getButtonA = driverPad.getButton(LogitechDualAction.LogitechButton.A);
		getButtonB_Operator = operatorPad.getButton(LogitechDualAction.LogitechButton.B);
		getButtonB = driverPad.getButton(LogitechDualAction.LogitechButton.B);
		getButtonLB_Operator = operatorPad.getButton(LogitechDualAction.LogitechButton.LEFT_BUMPER);
		getButtonLT_Operator = operatorPad.getButton(LogitechDualAction.LogitechButton.LEFT_TRIGGER);
		leftStickY_Operator = operatorPad.getValue(LogitechDualAction.LogitechControl.LEFT_STICK, LogitechDualAction.LogitechAxis.Y);
		getButotnRB_Operator = operatorPad.getButton(LogitechDualAction.LogitechButton.RIGHT_BUMPER);

		  
		SmartDashboard.putNumber("gyro", angle);
		
		
		//get air for pneumatics
		pressureSwitch = air.getPressureSwitchValue();
		if (!pressureSwitch) 
		{
			air.setClosedLoopControl(true);
		}

		//gets yaw from gyro continuously
		angle = ahrs.getYaw();
		
		//Dead band, restricts stick movement when less than 5% for all controls
		if (Math.abs(rightStickX) < 0.05)
		{
			rightStickX = 0;
		}
		if (Math.abs(leftStickY) < 0.05) 
		{
			leftStickY = 0;
		}
		if (Math.abs(leftStickX) < 0.05) 
		{
			leftStickX = 0;
		}

		//Preset angles for the robot - to be called with buttons A, B, X, Y
		rotateToAngle = false;
		if (getButtonSTART) 
		{
			ahrs.reset();
		}
		if (getButtonY) 
		{
			currentRotationRate = forwardPID();
			rotateToAngle = true;
		}
		else if (getButtonB) 
		{
			currentRotationRate = rightPID();
			rotateToAngle = true;
		}
		else if (getButtonA) 
		{
			currentRotationRate = backwardPID();
			rotateToAngle = true;
		}
		else if (getButtonX) 
		{
			currentRotationRate = leftPID();
			rotateToAngle = true;
		}

		if(!rotateToAngle)
		{
			turnController.disable();
			currentRotationRate = rightStickX;
		}

		//Calls upon the mecanumDrive_Cartesian method that sends specific power to the talons
		drivetrain.driveCartesian (leftStickX, leftStickY, currentRotationRate * 0.5, -angle);

		//Setting speeds for the claw's motors to intake or shoot out the cube
		//Left Bumper intakes, Left Trigger spits out cube 
		if(getButtonLT_Operator)
		{
			ClawOutput();
		}
		else if (getButtonLB_Operator) 
		{
			ClawIntake();
		}else {		// The motors are always pulling in the cube so wont fly out when turning
			ClawStandby();
		}

		//Setting positions for the pistons
		//Pressing (A) opens the claw, claw is closed at default
		if(getButtonB_Operator)
		{
			ClawOpen();
		}else
		{
			ClawClose();
		}

		//Move the elevator up or down
		if (leftStickY_Operator > 0.25)
		{
			ElevatorDown();
		}else if (leftStickY_Operator < -0.25)
		{
			ElevatorUp();
		}
		else {
			leftElevator.set(0);
			rightElevator.set(0);
		}
			


		//Stop taking air when pneumatics reaches 120 psi
		if (pressureSwitch == true) 
		{
			air.setClosedLoopControl(false);
		}
		
		//Right bumper on operator controller lowers claw when held, otherwise rotates up
		if(getButotnRB_Operator)
		{
			ClawRotateUp();
		}else
		{
			ClawRotateDown();
		}
	}

	public void pidWrite(double output) 
	{
		rotateToAngleRate = output;
	}

	//Preset method that pushes out "right stick X rotation" with PID - backward
	private double forwardPID() 
	{
		turnController.setSetpoint(0.0f);
		turnController.enable();
		return rotateToAngleRate;
	}
	//Preset method that pushes out "right stick X rotation" with PID - forward
	private double backwardPID() 
	{
		turnController.setSetpoint(180.0f);
		turnController.enable();
		return rotateToAngleRate;
	}
	//Preset method that pushes out "right stick X rotation" with PID - right
	private double rightPID() 
	{
		turnController.setSetpoint(90.0f);
		turnController.enable();
		return rotateToAngleRate;
	}
	//Preset method that pushes out "right stick X rotation" with PID - left
	private double leftPID() 
	{
		turnController.setSetpoint(-90.0f);
		turnController.enable();
		return rotateToAngleRate;
	}
	//Intakes the cube
	private void ClawIntake()
	{
		IntakeL.set(0.5);
		IntakeR.set(-0.5);
	}
	//Spits out the cube
	private void ClawOutput()
	{
		IntakeL.set(-0.5);
		IntakeR.set(0.5);
	}
	//Motors pulling cube in lightly to prevent losing the cube 
	private void ClawStandby()
	{
		IntakeL.set(0.15);
		IntakeR.set(-0.15);
	}

	//Close claw
	private void ClawClose()
	{
		claw.set(DoubleSolenoid.Value.kForward);	
	} 
	//Open claw
	private void ClawOpen()
	{
		claw.set(DoubleSolenoid.Value.kReverse);
	}
	//Elevator Up
	private void ElevatorUp()
	{
		leftElevator.set(-0.5);
		rightElevator.set(0.5);
	}
	//Elevator Down
	private void ElevatorDown()
	{
		leftElevator.set(0.5);
		rightElevator.set(-0.5);
	}
	
	//Claw Rotate Up - Standby Position
	private void ClawRotateUp()
	{
		pivot.setSpeed(0.4);
	}
	
	//Claw Rotate Down
	private void ClawRotateDown()
	{
		pivot.setSpeed(-0.4);
	}
}
